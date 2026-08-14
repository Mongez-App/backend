package com.smartstudy.planning.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public record JoinTeamResponse(
        @JsonProperty("org_id") String orgId,
        @JsonProperty("org_name") String orgName,
        @JsonProperty("team_id") String teamId,
        @JsonProperty("team_name") String teamName,
        @JsonProperty("status") String status,
        @JsonProperty("message") String message
) {
}
