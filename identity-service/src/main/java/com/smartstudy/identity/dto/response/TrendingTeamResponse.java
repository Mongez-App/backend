package com.smartstudy.identity.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TrendingTeamResponse(
        @JsonProperty("team_id") String teamId,
        @JsonProperty("name") String name,
        @JsonProperty("image_url") String imageUrl,
        @JsonProperty("organization_name") String organizationName,
        @JsonProperty("member_count") int memberCount,
        @JsonProperty("description") String description
) {}