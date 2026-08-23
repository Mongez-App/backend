package com.smartstudy.planning.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.smartstudy.planning.model.Priority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.time.LocalDate;

public record UpdateTaskRequest(
        @NotBlank @JsonProperty("title") String title,
        @Positive @JsonProperty("duration_minutes") Integer durationMinutes,
        @JsonProperty("priority") Priority priority,
        @JsonProperty("completed") @JsonAlias({"is_completed", "task_completed"}) Boolean isCompleted,
        @JsonProperty("scheduled_date") @JsonAlias("date") @JsonFormat(pattern = "yyyy-MM-dd") LocalDate date,
        @JsonProperty("sequence_order") Integer sequenceOrder,
        @PositiveOrZero @JsonProperty("active_spent_time") Integer activeSpentTime
) {
}
