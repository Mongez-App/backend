package com.smartstudy.planning.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

public record RescheduleRoadmapRequest(
        @NotEmpty @JsonProperty("block_ids") List<UUID> blockIds,
        @JsonProperty("reason") String reason
) {
}
