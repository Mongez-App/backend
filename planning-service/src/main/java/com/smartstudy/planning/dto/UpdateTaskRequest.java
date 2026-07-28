package com.smartstudy.planning.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.smartstudy.planning.model.Priority;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;

public record UpdateTaskRequest(
        @JsonProperty("title") String title,
        @Positive @JsonProperty("duration_minutes") Integer durationMinutes,
        @JsonProperty("priority") Priority priority,
        @JsonProperty("is_completed") Boolean isCompleted,
        @JsonProperty("date") LocalDate date,
        @JsonProperty("sequence_order") Integer sequenceOrder
) {
}
