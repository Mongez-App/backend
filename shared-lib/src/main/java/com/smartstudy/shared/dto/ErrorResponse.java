package com.smartstudy.shared.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ErrorResponse(
        @JsonProperty("error_code") String errorCode,
        @JsonProperty("message") String message,
        @JsonProperty("timestamp") String timestamp
) {
}
