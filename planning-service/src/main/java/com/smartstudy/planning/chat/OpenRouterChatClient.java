package com.smartstudy.planning.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartstudy.planning.chat.model.GeminiPrompt;
import com.smartstudy.planning.config.OpenRouterProperties;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Dedicated client for calling OpenRouter REST API using OpenAI-compatible chat completions.
 * Uses the OpenRouter Free Router ("openrouter/free") to automatically pick an available free model.
 */
@Service
public class OpenRouterChatClient {

    private static final Logger log = LoggerFactory.getLogger(OpenRouterChatClient.class);
    private static final String MODEL_NAME = "openrouter/free";

    private final RestClient openRouterRestClient;
    private final OpenRouterProperties openRouterProps;
    private final ObjectMapper objectMapper;

    public OpenRouterChatClient(RestClient openRouterRestClient,
                                OpenRouterProperties openRouterProps,
                                ObjectMapper objectMapper) {
        this.openRouterRestClient = openRouterRestClient;
        this.openRouterProps = openRouterProps;
        this.objectMapper = objectMapper;
    }

    /**
     * Call OpenRouter /chat/completions with the provided prompt.
     *
     * @param prompt the prompt to send
     * @return parsed structured response
     */
    public LlmStructuredResponse generate(GeminiPrompt prompt) {
        log.info("Trying OpenRouter model: {}", MODEL_NAME);

        String apiKey = openRouterProps.apiKey();
        if (apiKey != null) {
            apiKey = apiKey.trim();
        }

        if (apiKey == null || apiKey.isBlank()) {
            log.error("OPENROUTER_API_KEY is null or blank! Cannot authenticate with OpenRouter.");
            throw new ChatException(
                    "AI_AUTHENTICATION_FAILED",
                    "OPENROUTER_API_KEY environment variable is missing or empty.",
                    HttpStatus.UNAUTHORIZED,
                    "OpenRouter",
                    null
            );
        }

        String masked = apiKey.substring(0, Math.min(6, apiKey.length())) + "...";
        log.info("Authenticating with OpenRouter using key: {} (length={})", masked, apiKey.length());

        Map<String, Object> requestBody = buildOpenAiRequestBody(prompt);

        try {
            Map<String, Object> response = openRouterRestClient.post()
                    .uri("/chat/completions")
                    .header("Authorization", "Bearer " + apiKey)
                    .header("HTTP-Referer", "http://localhost:8082")
                    .header("X-Title", "SmartStudy")
                    .body(requestBody)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});

            log.info("OpenRouter succeeded.");
            String text = extractTextFromOpenRouterResponse(response);
            return LlmResponseParser.parse(text, "OpenRouter", objectMapper);

        } catch (HttpStatusCodeException e) {
            int statusCode = e.getStatusCode().value();
            if (statusCode == HttpStatus.TOO_MANY_REQUESTS.value() || statusCode == 402) { // 429 or 402 Payment/Quota
                log.warn("OpenRouter model: {} returned {}.", MODEL_NAME, statusCode);
                log.warn("All AI providers exhausted.");
                throw new ChatException(
                        "AI_QUOTA_EXCEEDED",
                        "All Gemini models are unavailable. OpenRouter free quota is also exhausted. Please try again later or configure another AI provider/API key.",
                        HttpStatus.TOO_MANY_REQUESTS,
                        "OpenRouter",
                        e
                );
            } else if (statusCode == HttpStatus.UNAUTHORIZED.value() || statusCode == HttpStatus.FORBIDDEN.value()) {
                log.error("OpenRouter authentication failed: status={}", statusCode);
                throw new ChatException(
                        "AI_AUTHENTICATION_FAILED",
                        "Authentication with OpenRouter failed. Check your OPENROUTER_API_KEY.",
                        HttpStatus.UNAUTHORIZED,
                        "OpenRouter",
                        e
                );
            } else {
                log.error("OpenRouter call failed: status={} | body={}", statusCode, e.getResponseBodyAsString());
                throw new ChatException(
                        "AI_RESPONSE_FAILED",
                        "OpenRouter provider returned error: " + statusCode,
                        HttpStatus.BAD_GATEWAY,
                        "OpenRouter",
                        e
                );
            }
        } catch (ResourceAccessException e) {
            log.error("OpenRouter connection/timeout error: {}", e.getMessage());
            throw new ChatException(
                    "AI_TIMEOUT",
                    "Request to OpenRouter provider timed out or failed to connect.",
                    HttpStatus.GATEWAY_TIMEOUT,
                    "OpenRouter",
                    e
            );
        } catch (ChatException e) {
            throw e;
        } catch (Exception e) {
            log.error("OpenRouter call failed unexpectedly: {}", e.getMessage());
            throw new ChatException(
                    "AI_RESPONSE_FAILED",
                    "Failed to generate OpenRouter AI response: " + e.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "OpenRouter",
                    e
            );
        }
    }

    private Map<String, Object> buildOpenAiRequestBody(GeminiPrompt prompt) {
        List<Map<String, String>> messages = new ArrayList<>();
        if (prompt != null && prompt.contents() != null) {
            for (GeminiPrompt.Content c : prompt.contents()) {
                String role = "user".equalsIgnoreCase(c.role()) ? "user" : "assistant";
                String contentText = "";
                if (c.parts() != null && !c.parts().isEmpty()) {
                    contentText = c.parts().get(0).text();
                }
                messages.add(Map.of("role", role, "content", contentText));
            }
        }

        Map<String, Object> body = new HashMap<>();
        body.put("model", MODEL_NAME);
        body.put("messages", messages);
        body.put("temperature", 0.3);
        return body;
    }

    @SuppressWarnings("unchecked")
    private String extractTextFromOpenRouterResponse(Map<String, Object> response) {
        if (response == null) {
            throw new ChatException("AI_RESPONSE_FAILED", "OpenRouter API returned null response");
        }
        List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
        if (choices == null || choices.isEmpty()) {
            throw new ChatException("AI_RESPONSE_FAILED", "OpenRouter response contained no choices");
        }
        Map<String, Object> messageObj = (Map<String, Object>) choices.get(0).get("message");
        if (messageObj == null) {
            throw new ChatException("AI_RESPONSE_FAILED", "OpenRouter choice contained no message");
        }
        return (String) messageObj.get("content");
    }
}
