package com.smartstudy.planning.chat.model;

import java.util.List;

/**
 * Strongly typed representation of a Gemini generateContent request.
 * Immutable and serialized to JSON by GeminiChatClient.
 *
 * Structure mirrors the Gemini REST API:
 * {
 *   "contents": [ { "role": "...", "parts": [ { "text": "..." } ] } ],
 *   "generationConfig": { "temperature": ..., "maxOutputTokens": ..., "responseMimeType": "..." }
 * }
 */
public record GeminiPrompt(
    List<Content> contents,
    GenerationConfig generationConfig
) {

    /**
     * A single turn in the conversation (system instruction, user message,
     * or model response).
     *
     * @param role  "user" or "model" (Gemini API roles)
     * @param parts list of content parts (typically a single text part)
     */
    public record Content(
        String role,
        List<Part> parts
    ) {
        /**
         * Convenience factory for a single-text content turn.
         */
        public static Content of(String role, String text) {
            return new Content(role, List.of(new Part(text)));
        }
    }

    /**
     * A content part within a turn. For the chat MVP, only text parts
     * are used. The record is extensible for future multimodal support.
     */
    public record Part(
        String text
    ) {}

    /**
     * Controls the LLM generation behavior.
     *
     * @param temperature       sampling temperature (0.0–1.0)
     * @param maxOutputTokens   maximum tokens in the response
     * @param responseMimeType  "application/json" for structured output
     */
    public record GenerationConfig(
        double temperature,
        int maxOutputTokens,
        String responseMimeType
    ) {}
}
