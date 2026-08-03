package com.smartstudy.planning.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Structured error response for REST API error handling.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
    @JsonProperty("error_code") String errorCode,
    @JsonProperty("error") String error,
    @JsonProperty("message") String message,
    @JsonProperty("provider") String provider,
    @JsonProperty("timestamp") String timestamp
) {
    public ErrorResponse(String errorCode, String message, String timestamp) {
        this(errorCode, errorCode, message, null, timestamp);
    }

    public ErrorResponse(String errorCode, String message, String provider, String timestamp) {
        this(errorCode, errorCode, message, provider, timestamp);
    }
}
