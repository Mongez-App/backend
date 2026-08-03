package com.smartstudy.planning.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/**
 * Single message in the chat history.
 */
public record ChatMessageResponse(
    @JsonProperty("message_id") String messageId,
    @JsonProperty("role") String role,
    @JsonProperty("content") String content,
    @JsonProperty("created_at") Instant createdAt
) {}
