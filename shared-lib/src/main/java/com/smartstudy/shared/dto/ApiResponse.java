package com.smartstudy.shared.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ApiResponse<T>(
        @JsonProperty("data") T data,
        @JsonProperty("message") String message
) {
}
