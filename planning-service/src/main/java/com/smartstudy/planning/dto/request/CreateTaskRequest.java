package com.smartstudy.planning.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.smartstudy.planning.model.Priority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.time.LocalDate;
import java.util.UUID;

public record CreateTaskRequest(
        @NotBlank @JsonProperty("title") String title,
        @NotNull @Positive @JsonProperty("duration_minutes") Integer durationMinutes,
        @JsonProperty("priority") Priority priority,
        @NotNull @JsonProperty("scheduled_date") @JsonAlias("date") @JsonFormat(pattern = "yyyy-MM-dd") LocalDate date,
        @NotNull @JsonProperty("course_id") UUID courseId,
        @JsonProperty("sequence_order") Integer sequenceOrder,
        @PositiveOrZero @JsonProperty("active_spent_time") Integer activeSpentTime
) {
}
