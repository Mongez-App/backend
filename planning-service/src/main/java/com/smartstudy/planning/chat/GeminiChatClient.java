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

import java.util.ArrayList;
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
     * Models tried after the configured one, in order.
     */
    private static final List<String> FALLBACK_MODELS = List.of(
            "models/gemini-2.5-flash",
            "models/gemini-2.0-flash",
            "models/gemini-2.0-flash-lite",
            "models/gemini-2.5-pro"
    );

    /**
     * The configured model ({@code gemini.chat.model} / {@code GEMINI_CHAT_MODEL})
     * first, then the fallbacks, with duplicates removed. Without this the
     * configured value would be silently ignored, since only the hardcoded list
     * was ever tried.
     */
    private List<String> modelsToTry() {
        List<String> models = new ArrayList<>();
        String configured = geminiProps.chat() != null ? geminiProps.chat().model() : null;
        if (configured != null && !configured.isBlank()) {
            models.add(qualify(configured));
        }
        for (String fallback : FALLBACK_MODELS) {
            String qualified = qualify(fallback);
            if (!models.contains(qualified)) {
                models.add(qualified);
            }
        }
        return models;
    }

    private static String qualify(String model) {
        return model.startsWith("models/") ? model : "models/" + model;
    }

    /**
     * Call Gemini generateContent with the assembled prompt.
     *
     * @param prompt the fully assembled, typed prompt built by PromptBuilder
     * @return parsed structured response
     * @throws ChatException if the API call fails or response is unparseable
     */
    public LlmStructuredResponse generate(GeminiPrompt prompt) {
        Exception lastException = null;

        for (String model : modelsToTry()) {
            String url = "/" + model + ":generateContent";

            log.info("Trying Gemini model: {}", model);

            try {
                Map<String, Object> response = geminiRestClient.post()
                        .uri(url)
                        .body(prompt)
                        .retrieve()
                        .body(new ParameterizedTypeReference<>() {});

                String text = extractTextFromResponse(response);
                return LlmResponseParser.parse(text, "Gemini", objectMapper);

            } catch (HttpStatusCodeException e) {
                lastException = e;
                int statusCode = e.getStatusCode().value();

                // An auth failure is the same for every model — retrying the rest
                // just burns time before returning the same answer.
                if (statusCode == HttpStatus.UNAUTHORIZED.value()
                        || statusCode == HttpStatus.FORBIDDEN.value()) {
                    throw new ChatException(
                            "AI_AUTHENTICATION_FAILED",
                            "Authentication with the AI provider failed. Check your GEMINI_API_KEY.",
                            HttpStatus.UNAUTHORIZED,
                            "Gemini",
                            e
                    );
                }

                log.warn("Gemini model {} failed with status {}, trying next...", model, statusCode);
            } catch (ChatException e) {
                throw e;
            } catch (ResourceAccessException e) {
                lastException = e;
                log.warn("Gemini model {} failed with network timeout/error, trying next...", model);
            } catch (Exception e) {
                lastException = e;
                log.warn("Gemini model {} failed unexpectedly: {}, trying next...", model, e.getMessage());
            }
        }

        // Every Gemini model failed for a non-auth reason. OpenRouter is the last
        // resort; if it is unconfigured or also failing it throws a ChatException
        // that names the real cause, which beats reporting a stale Gemini error.
        log.warn("All Gemini models failed (last error: {}). Switching to OpenRouter...",
                lastException != null ? lastException.getMessage() : "unknown");
        return openRouterChatClient.generate(prompt);
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
        Map<String, Object> candidate = candidates.get(0);

        // A candidate can come back with no content at all when generation stopped
        // early — SAFETY means a blocked answer, MAX_TOKENS a truncated one. Both
        // used to surface as a null answer persisted into the chat history.
        Object finishReason = candidate.get("finishReason");
        Map<String, Object> content = (Map<String, Object>) candidate.get("content");
        if (content == null) {
            throw new ChatException("AI_RESPONSE_FAILED",
                    "Gemini returned no content (finishReason: " + finishReason + ")");
        }
        List<Map<String, Object>> parts =
                (List<Map<String, Object>>) content.get("parts");
        if (parts == null || parts.isEmpty()) {
            throw new ChatException("AI_RESPONSE_FAILED",
                    "Gemini returned no content parts (finishReason: " + finishReason + ")");
        }
        return (String) parts.get(0).get("text");
    }
}
