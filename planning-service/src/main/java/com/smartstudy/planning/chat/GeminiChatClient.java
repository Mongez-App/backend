package com.smartstudy.planning.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartstudy.planning.chat.model.GeminiPrompt;
import com.smartstudy.planning.config.GeminiProperties;
import com.smartstudy.planning.dto.response.LlmStructuredResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * Calls the Gemini generateContent REST API and returns a parsed
 * {@link LlmStructuredResponse}.
 * <p>
 * Accepts a strongly typed {@link GeminiPrompt} — Jackson handles
 * serialization automatically. Reuses the existing {@code geminiRestClient}
 * bean (already configured with the API key).
 * </p>
 */
@Service
public class GeminiChatClient {

    private static final Logger log = LoggerFactory.getLogger(GeminiChatClient.class);

    private final RestClient geminiRestClient;
    private final GeminiProperties geminiProps;
    private final ObjectMapper objectMapper;
    private final OpenRouterChatClient openRouterChatClient;

    public GeminiChatClient(RestClient geminiRestClient,
                            GeminiProperties geminiProps,
                            ObjectMapper objectMapper,
                            OpenRouterChatClient openRouterChatClient) {
        this.geminiRestClient = geminiRestClient;
        this.geminiProps = geminiProps;
        this.objectMapper = objectMapper;
        this.openRouterChatClient = openRouterChatClient;
    }

    /**
     * Fallback order of models retrieved from Gemini API GET /v1beta/models.
     */
    private static final List<String> FALLBACK_MODELS = List.of(
            "models/gemini-2.5-flash",
            "models/gemini-2.0-flash",
            "models/gemini-2.0-flash-lite",
            "models/gemini-2.5-pro"
    );

    /**
     * Call Gemini generateContent with the assembled prompt.
     *
     * @param prompt the fully assembled, typed prompt built by PromptBuilder
     * @return parsed structured response
     * @throws ChatException if the API call fails or response is unparseable
     */
    public LlmStructuredResponse generate(GeminiPrompt prompt) {
        boolean providerUnavailable = true;
        int failedCount = 0;
        Exception lastException = null;

        for (String model : FALLBACK_MODELS) {
            String cleanModel = model.startsWith("models/") ? model : "models/" + model;
            String url = "/" + cleanModel + ":generateContent";

            log.info("Trying Gemini model: {}", cleanModel);

            try {
                Map<String, Object> response = geminiRestClient.post()
                        .uri(url)
                        .body(prompt)
                        .retrieve()
                        .body(new ParameterizedTypeReference<>() {});

                String text = extractTextFromResponse(response);
                return parseStructuredResponse(text);

            } catch (HttpStatusCodeException e) {
                failedCount++;
                lastException = e;
                int statusCode = e.getStatusCode().value();

                if (statusCode == HttpStatus.TOO_MANY_REQUESTS.value()) { // 429
                    log.warn("Gemini returned 429 for model {}", cleanModel);
                } else if (statusCode == HttpStatus.NOT_FOUND.value()) { // 404
                    log.warn("Model {} failed with 404 Not Found, trying next...", cleanModel);
                } else if (e.getStatusCode().is5xxServerError()) { // 5xx
                    log.warn("Model {} failed with {}, trying next...", cleanModel, statusCode);
                } else if (statusCode == HttpStatus.UNAUTHORIZED.value() || statusCode == HttpStatus.FORBIDDEN.value()) {
                    providerUnavailable = false;
                    throw new ChatException(
                            "AI_AUTHENTICATION_FAILED",
                            "Authentication with the AI provider failed. Check your GEMINI_API_KEY.",
                            HttpStatus.UNAUTHORIZED,
                            "Gemini",
                            e
                    );
                } else {
                    providerUnavailable = false;
                    log.warn("Model {} failed with status {}, trying next...", cleanModel, statusCode);
                }
            } catch (ResourceAccessException e) {
                failedCount++;
                lastException = e;
                log.warn("Model {} failed with network timeout/error, trying next...", cleanModel);
            } catch (ChatException e) {
                throw e;
            } catch (Exception e) {
                failedCount++;
                lastException = e;
                providerUnavailable = false;
                log.warn("Model {} failed unexpectedly: {}, trying next...", cleanModel, e.getMessage());
            }
        }

        if (failedCount > 0 && providerUnavailable) {
            log.info("Switching to OpenRouter...");
            return openRouterChatClient.generate(prompt);
        }

        throw new ChatException(
                "AI_RESPONSE_FAILED",
                "All Gemini models failed. Last error: " + (lastException != null ? lastException.getMessage() : "Unknown error"),
                HttpStatus.BAD_GATEWAY,
                "Gemini",
                lastException
        );
    }

    @SuppressWarnings("unchecked")
    private String extractTextFromResponse(Map<String, Object> response) {
        if (response == null) {
            throw new ChatException("AI_RESPONSE_FAILED",
                    "Gemini API returned null response");
        }
        // Navigate: candidates[0].content.parts[0].text
        List<Map<String, Object>> candidates =
                (List<Map<String, Object>>) response.get("candidates");
        if (candidates == null || candidates.isEmpty()) {
            throw new ChatException("AI_RESPONSE_FAILED",
                    "Gemini response contained no candidates");
        }
        Map<String, Object> content =
                (Map<String, Object>) candidates.get(0).get("content");
        if (content == null) {
            throw new ChatException("AI_RESPONSE_FAILED",
                    "Gemini response candidate contained no content");
        }
        List<Map<String, Object>> parts =
                (List<Map<String, Object>>) content.get("parts");
        if (parts == null || parts.isEmpty()) {
            throw new ChatException("AI_RESPONSE_FAILED",
                    "Gemini response content contained no parts");
        }
        return (String) parts.get(0).get("text");
    }

    private LlmStructuredResponse parseStructuredResponse(String text) {
        try {
            // Strip markdown code fences if present (```json ... ```)
            String cleaned = text.strip();
            if (cleaned.startsWith("```")) {
                int firstNewline = cleaned.indexOf('\n');
                int lastFence = cleaned.lastIndexOf("```");
                if (firstNewline != -1 && lastFence > firstNewline) {
                    cleaned = cleaned.substring(firstNewline + 1, lastFence).strip();
                }
            }
            return objectMapper.readValue(cleaned, LlmStructuredResponse.class);
        } catch (Exception e) {
            log.warn("Failed to parse structured LLM response, using fallback: {}",
                    e.getMessage());
            // Fallback: wrap raw text as a low-confidence answer
            return new LlmStructuredResponse(
                    text, false, "LOW", null, List.of());
        }
    }
}
