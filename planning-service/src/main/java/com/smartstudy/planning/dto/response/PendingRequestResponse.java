package com.smartstudy.planning.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

public record PendingRequestResponse(
        @JsonProperty("team_id") String teamId,
        @JsonProperty("name") String name,
        @JsonProperty("image_url") String imageUrl,
        @JsonProperty("organization_name") String organizationName,
        @JsonProperty("applied_at") Instant appliedAt,
        @JsonProperty("status") String status
) {
}
