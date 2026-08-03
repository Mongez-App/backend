package com.smartstudy.planning.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Response returned after sending a chat message (POST).
 * Contains the user message and the assistant response.
 */
public record ChatResponse(
    @JsonProperty("messages") List<ChatMessageResponse> messages
) {}
