package com.smartstudy.planning.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;

/**
 * Single message in the chat history.
 * <p>
 * The four grounding fields are assistant-only and are omitted entirely for USER
 * messages, so the shape of a user turn is unchanged. They let a client show
 * whether an answer was backed by the student's own course material and, if so,
 * which sections it came from.
 * </p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChatMessageResponse(
    @JsonProperty("message_id") String messageId,
    @JsonProperty("role") String role,
    @JsonProperty("content") String content,
    @JsonProperty("created_at") Instant createdAt,
    @JsonProperty("used_context") Boolean usedContext,
    @JsonProperty("confidence") String confidence,
    @JsonProperty("suggested_follow_up") String suggestedFollowUp,
    @JsonProperty("sources") List<LlmStructuredResponse.SourceReference> sources
) {

    /** A user turn, which carries no grounding metadata. */
    public static ChatMessageResponse userMessage(String messageId, String role,
                                                  String content, Instant createdAt) {
        return new ChatMessageResponse(messageId, role, content, createdAt,
                null, null, null, null);
    }
}
