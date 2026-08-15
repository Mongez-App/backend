package com.smartstudy.planning.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public record JoinTeamApiResponse(
        @JsonProperty("success") boolean success,
        @JsonProperty("data") JoinTeamResponse data,
        @JsonProperty("message") String message
) {
}
