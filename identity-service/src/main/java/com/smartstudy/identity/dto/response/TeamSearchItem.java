package com.smartstudy.identity.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TeamSearchItem(
        @JsonProperty("team_id") String teamId,
        @JsonProperty("name") String name,
        @JsonProperty("organization_name") String organizationName,
        @JsonProperty("status") String status
) {}