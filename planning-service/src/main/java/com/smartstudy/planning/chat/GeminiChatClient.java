package com.smartstudy.planning.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartstudy.planning.chat.model.GeminiPrompt;
import com.smartstudy.planning.config.GeminiProperties;
import com.smartstudy.planning.dto.response.LlmStructuredResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
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
     * Call Gemini generateContent with the assembled prompt.
     *
     * @param prompt the fully assembled, typed prompt built by PromptBuilder
     * @return parsed structured response
     * @throws ChatException if the API call fails or response is unparseable
     */
    public LlmStructuredResponse generate(GeminiPrompt prompt) {
        String model = geminiProps.chat().model();
        String url = "/models/" + model + ":generateContent";

        try {
            Map<String, Object> response = geminiRestClient.post()
                    .uri(url)
                    .body(prompt)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});

            // Extract text from response
            String text = extractTextFromResponse(response);

            // Parse structured JSON into LlmStructuredResponse
            return parseStructuredResponse(text);

        } catch (ChatException e) {
            throw e;
        } catch (Exception e) {
            log.error("Gemini API call failed: {}", e.getMessage(), e);
            throw new ChatException("AI_RESPONSE_FAILED",
                    "Failed to generate AI response: " + e.getMessage(), e);
        }
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
