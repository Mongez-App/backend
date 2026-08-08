package com.smartstudy.planning.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public record JoinTeamResponse(
        @JsonProperty("orgId") String orgId,
        @JsonProperty("orgName") String orgName,
        @JsonProperty("teamId") String teamId,
        @JsonProperty("team_name") String teamName,
        @JsonProperty("status") String status,
        @JsonProperty("message") String message
) {
}
