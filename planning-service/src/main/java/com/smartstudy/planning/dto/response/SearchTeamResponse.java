package com.smartstudy.planning.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public record SearchTeamResponse(
        @JsonProperty("team_id") String teamId,
        @JsonProperty("name") String name,
        @JsonProperty("organization_name") String organizationName,
        @JsonProperty("status") String status,
        @JsonProperty("image_url") String imageUrl
) {
}
