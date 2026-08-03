package com.smartstudy.planning.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

/**
 * Inbound REST payload for sending a chat message.
 */
public record ChatMessageRequest(
    @NotBlank(message = "Message must not be empty")
    @JsonProperty("message") String message
) {}
