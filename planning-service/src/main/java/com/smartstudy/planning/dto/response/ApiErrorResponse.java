package com.smartstudy.planning.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record ApiErrorResponse(
        @JsonProperty("success") boolean success,
        @JsonProperty("error") String error,
        @JsonProperty("message") String message,
        @JsonProperty("details") List<String> details
) {
}
