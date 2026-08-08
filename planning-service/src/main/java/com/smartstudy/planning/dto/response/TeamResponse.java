package com.smartstudy.planning.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record TeamResponse(
        @JsonProperty("team_id") String teamId,
        @JsonProperty("name") String name,
        @JsonProperty("organization_name") String organizationName,
        @JsonProperty("image_url") String imageUrl,
        @JsonProperty("completion_percentage") double completionPercentage,
        @JsonProperty("events") List<TeamEventSummary> events
) {
}
