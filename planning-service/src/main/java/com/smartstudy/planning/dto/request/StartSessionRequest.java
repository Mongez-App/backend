package com.smartstudy.planning.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

public record StartSessionRequest(
        @NotNull @JsonProperty("course_id") UUID courseId,
        @NotNull @Positive @JsonProperty("estimated_duration_minutes") Integer estimatedDurationMinutes,
        @JsonProperty("linked_task_id") UUID linkedTaskId
) {
}
