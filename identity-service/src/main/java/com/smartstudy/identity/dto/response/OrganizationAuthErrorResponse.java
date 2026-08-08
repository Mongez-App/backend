package com.smartstudy.identity.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public record OrganizationAuthErrorResponse(
        @JsonProperty("error") String error,
        @JsonProperty("message") String message
) {
}
