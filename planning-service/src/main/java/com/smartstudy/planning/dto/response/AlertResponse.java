package com.smartstudy.planning.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AlertResponse(
        @JsonProperty("message") String message
) {
}
