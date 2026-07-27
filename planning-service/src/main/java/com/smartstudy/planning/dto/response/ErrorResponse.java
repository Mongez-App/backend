package com.smartstudy.planning.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Structured error response for WebSocket error handling.
 */
public record ErrorResponse(
    @JsonProperty("error_code") String errorCode,
    @JsonProperty("message") String message,
    @JsonProperty("timestamp") String timestamp
) {}
