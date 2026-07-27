package com.smartstudy.planning.chat;

import com.smartstudy.planning.chat.model.GeminiPrompt;
import com.smartstudy.planning.chat.model.RetrievedChunk;
import com.smartstudy.planning.config.GeminiProperties;
import com.smartstudy.planning.model.ChatMessage;
import com.smartstudy.planning.model.MessageRole;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Assembles a complete GeminiPrompt from chat history, task context,
 * retrieved chunks, and the current user message.
 * <p>
 * This component has NO side effects — it does not call the LLM,
 * query the database, or interact with Qdrant.
 * </p>
 */
@Component
public class PromptBuilder {

    private final GeminiProperties geminiProps;

    public PromptBuilder(GeminiProperties geminiProps) {
        this.geminiProps = geminiProps;
    }

    /**
     * Build a complete GeminiPrompt for the generateContent API.
     *
     * @param chatHistory     previous messages (chronological order)
     * @param taskTitle       the task's title
     * @param courseName      the course name (nullable if task has no course)
     * @param retrievedChunks top-K chunks from Qdrant (may be empty)
     * @param userMessage     the current user message
     * @param language        preferred language ("en" or "ar")
     * @return a fully assembled, immutable GeminiPrompt
     */
    public GeminiPrompt buildPrompt(
            List<ChatMessage> chatHistory,
            String taskTitle,
            String courseName,
            List<RetrievedChunk> retrievedChunks,
            String userMessage,
            String language) {

        List<GeminiPrompt.Content> contents = new ArrayList<>();

        // 1. System prompt (sent as a "user" turn — Gemini has no system role)
        String systemText = buildSystemPromptText(language);
        contents.add(GeminiPrompt.Content.of("user", systemText));
        contents.add(GeminiPrompt.Content.of("model", "Understood. I will follow these instructions."));

        // 2. Previous chat history
        for (ChatMessage msg : chatHistory) {
            String role = msg.getRole() == MessageRole.USER ? "user" : "model";
            contents.add(GeminiPrompt.Content.of(role, msg.getContent()));
        }

        // 3. Task context + 4. Retrieved chunks + 5. User message
        //    Combined into a single user turn to keep the prompt clean
        String contextBlock = buildContextBlock(taskTitle, courseName, retrievedChunks, userMessage);
        contents.add(GeminiPrompt.Content.of("user", contextBlock));

        // Generation config from application properties
        GeminiPrompt.GenerationConfig config = new GeminiPrompt.GenerationConfig(
                geminiProps.chat().temperature(),
                geminiProps.chat().maxOutputTokens(),
                "application/json"
        );

        return new GeminiPrompt(contents, config);
    }

    private String buildSystemPromptText(String language) {
        String lang = (language != null && !language.isBlank()) ? language : "en";

        return """
                You are SmartStudy AI Tutor — a professional, concise, and accurate \
                educational assistant.

                RULES:
                1. Stay strictly within the educational context of the student's task and \
                course materials.
                2. ALWAYS prioritize the retrieved study material chunks provided below. \
                Use them as your primary source of truth.
                3. If the retrieved context contains the answer, use it. Do NOT hallucinate \
                or invent information that contradicts the provided materials.
                4. If the retrieved context is insufficient, clearly state: \
                "The provided study materials do not cover this topic in detail. Based \
                on general knowledge: ..."
                5. Answer in the student's preferred language: %s.
                6. Be concise. Avoid unnecessary verbosity. Focus on clarity.
                7. Use examples when they aid understanding.
                8. Maintain consistency across the conversation.

                RESPONSE FORMAT:
                You MUST respond with valid JSON in this exact structure:
                {
                  "answer": "<your educational explanation>",
                  "used_context": true | false,
                  "confidence": "HIGH" | "MEDIUM" | "LOW",
                  "suggested_follow_up": "<a follow-up question the student might ask>",
                  "sources": [
                    { "section": "<section title>", "page": <page number> }
                  ]
                }

                If no retrieved context was provided, set "used_context" to false, \
                "confidence" to "MEDIUM", and "sources" to an empty array.
                """.formatted(lang);
    }

    private String buildContextBlock(String taskTitle, String courseName,
                                     List<RetrievedChunk> chunks, String userMessage) {
        StringBuilder sb = new StringBuilder();

        // Task context
        sb.append("=== CURRENT TASK ===").append("\n");
        sb.append("Task: ").append(taskTitle).append("\n");
        if (courseName != null) {
            sb.append("Course: ").append(courseName).append("\n");
        }

        // Retrieved chunks
        if (chunks != null && !chunks.isEmpty()) {
            sb.append("\n=== RETRIEVED STUDY MATERIAL ===").append("\n");
            for (int i = 0; i < chunks.size(); i++) {
                RetrievedChunk chunk = chunks.get(i);
                sb.append("[Chunk ").append(i + 1).append("] ");
                if (chunk.sectionTitle() != null && !chunk.sectionTitle().isBlank()) {
                    sb.append("Section: ").append(chunk.sectionTitle()).append(" | ");
                }
                sb.append("Pages ").append(chunk.pageStart()).append("-").append(chunk.pageEnd());
                sb.append("\n").append(chunk.text()).append("\n\n");
            }
        }

        // User message
        sb.append("\n=== STUDENT QUESTION ===").append("\n");
        sb.append(userMessage);

        return sb.toString();
    }
}
