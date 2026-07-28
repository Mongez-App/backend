package com.smartstudy.planning.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.smartstudy.planning.model.Priority;

import java.time.LocalDate;
import java.util.UUID;

public record TaskResponse(
        @JsonProperty("task_id") UUID taskId,
        @JsonProperty("title") String title,
        @JsonProperty("duration_minutes") Integer durationMinutes,
        @JsonProperty("priority") Priority priority,
        @JsonProperty("is_completed") boolean isCompleted,
        @JsonProperty("date") LocalDate date,
        @JsonProperty("course_id") UUID courseId,
        @JsonProperty("sequence_order") Integer sequenceOrder
) {
}
