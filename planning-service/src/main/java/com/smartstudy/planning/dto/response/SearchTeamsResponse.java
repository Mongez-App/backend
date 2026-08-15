package com.smartstudy.planning.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record SearchTeamsResponse(
        @JsonProperty("success") boolean success,
        @JsonProperty("data") List<SearchTeamResponse> data
) {
}
