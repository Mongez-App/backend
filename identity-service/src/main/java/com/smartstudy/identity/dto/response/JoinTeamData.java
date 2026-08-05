package com.smartstudy.identity.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public record JoinTeamData(
        @JsonProperty("orgId") String orgId,
        @JsonProperty("orgName") String orgName,
        @JsonProperty("teamId") String teamId,
        @JsonProperty("status") String status
) {}