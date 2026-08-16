package com.smartstudy.planning.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartstudy.planning.dto.response.LlmStructuredResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;

import java.util.List;

/**
 * Turns raw LLM output into an {@link LlmStructuredResponse}.
 * <p>
 * Shared by {@link GeminiChatClient} and {@link OpenRouterChatClient}, which
 * receive the same JSON contract because {@code PromptBuilder} asks both
 * providers for the same response shape.
 * </p>
 */
final class LlmResponseParser {

    private static final Logger log = LoggerFactory.getLogger(LlmResponseParser.class);

    private LlmResponseParser() {
    }

    /**
     * Parse the model's text into a structured response.
     * <p>
     * A model that returns no text at all (blocked by a safety filter, truncated
     * at the token limit, or otherwise empty) is an error, not a low-confidence
     * answer — returning a wrapper here would persist a chat message with null
     * content and show the student an empty reply.
     * </p>
     *
     * @param text     raw text from the provider, may be null
     * @param provider provider name, used for the error message
     * @return the parsed response, or a low-confidence wrapper if the text was
     *         present but not valid JSON
     * @throws ChatException if the provider returned no usable text
     */
    static LlmStructuredResponse parse(String text, String provider, ObjectMapper objectMapper) {
        if (text == null || text.isBlank()) {
            throw new ChatException(
                    "AI_RESPONSE_FAILED",
                    "The AI provider returned an empty response. This usually means the "
                            + "answer was blocked by a safety filter or truncated at the output "
                            + "token limit.",
                    HttpStatus.BAD_GATEWAY,
                    provider,
                    null);
        }

        try {
            return objectMapper.readValue(stripCodeFences(text), LlmStructuredResponse.class);
        } catch (Exception e) {
            // The model answered but ignored the JSON contract. The prose is still
            // useful, so surface it rather than failing the whole request.
            log.warn("Failed to parse structured {} response, falling back to raw text: {}",
                    provider, e.getMessage());
            return new LlmStructuredResponse(text, false, "LOW", null, List.of());
        }
    }

    /**
     * Strip a leading ```json fence and its closing counterpart, which models add
     * even when asked for raw JSON.
     */
    private static String stripCodeFences(String text) {
        String cleaned = text.strip();
        if (!cleaned.startsWith("```")) {
            return cleaned;
        }
        int firstNewline = cleaned.indexOf('\n');
        int lastFence = cleaned.lastIndexOf("```");
        if (firstNewline != -1 && lastFence > firstNewline) {
            return cleaned.substring(firstNewline + 1, lastFence).strip();
        }
        return cleaned;
    }
}
