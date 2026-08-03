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

    public GeminiChatClient(RestClient geminiRestClient,
                            GeminiProperties geminiProps,
                            ObjectMapper objectMapper) {
        this.geminiRestClient = geminiRestClient;
        this.geminiProps = geminiProps;
        this.objectMapper = objectMapper;
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
        boolean allQuotaFailures = true;
        int failedCount = 0;
        Exception lastException = null;

        for (String model : FALLBACK_MODELS) {
            String cleanModel = model.startsWith("models/") ? model : "models/" + model;
            String url = "/" + cleanModel + ":generateContent";

            log.info("Trying model: {}", cleanModel);

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
                    log.warn("Model {} failed with 429, trying next...", cleanModel);
                } else if (statusCode == HttpStatus.NOT_FOUND.value()) { // 404
                    allQuotaFailures = false;
                    log.warn("Model {} failed with 404, trying next...", cleanModel);
                } else if (e.getStatusCode().is5xxServerError()) { // 5xx
                    allQuotaFailures = false;
                    log.warn("Model {} failed with {}, trying next...", cleanModel, statusCode);
                } else if (statusCode == HttpStatus.UNAUTHORIZED.value() || statusCode == HttpStatus.FORBIDDEN.value()) {
                    throw new ChatException(
                            "AI_AUTHENTICATION_FAILED",
                            "Authentication with the AI provider failed. Check your GEMINI_API_KEY.",
                            HttpStatus.UNAUTHORIZED,
                            "Gemini",
                            e
                    );
                } else {
                    allQuotaFailures = false;
                    log.warn("Model {} failed with status {}, trying next...", cleanModel, statusCode);
                }
            } catch (ResourceAccessException e) {
                failedCount++;
                lastException = e;
                allQuotaFailures = false;
                log.warn("Model {} failed with network timeout/error, trying next...", cleanModel);
            } catch (ChatException e) {
                throw e;
            } catch (Exception e) {
                failedCount++;
                lastException = e;
                allQuotaFailures = false;
                log.warn("Model {} failed unexpectedly: {}, trying next...", cleanModel, e.getMessage());
            }
        }

        if (failedCount > 0 && allQuotaFailures) {
            throw new ChatException(
                    "AI_QUOTA_EXCEEDED",
                    "The AI provider quota has been exceeded for all available models. Please try again later or update GEMINI_API_KEY.",
                    HttpStatus.TOO_MANY_REQUESTS,
                    "Gemini",
                    lastException
            );
        }

        throw new ChatException(
                "AI_RESPONSE_FAILED",
                "All AI provider models failed. Last error: " + (lastException != null ? lastException.getMessage() : "Unknown error"),
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
