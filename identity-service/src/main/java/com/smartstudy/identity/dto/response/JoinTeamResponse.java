package com.smartstudy.identity.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public record JoinTeamResponse(
        @JsonProperty("success") boolean success,
        @JsonProperty("data") JoinTeamData data,
        @JsonProperty("message") String message
) {}