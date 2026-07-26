package com.smartstudy.planning.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

public record RescheduleRoadmapRequest(
        @NotEmpty @JsonProperty("task_ids") List<UUID> taskIds,
        @JsonProperty("reason") String reason
) {
}
