package com.smartstudy.planning.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.smartstudy.planning.model.Priority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;
import java.util.UUID;

public record CreateTaskRequest(
        @NotBlank @JsonProperty("title") String title,
        @NotNull @Positive @JsonProperty("duration_minutes") Integer durationMinutes,
        @NotNull @JsonProperty("priority") Priority priority,
        @JsonProperty("scheduled_date") @JsonAlias("date") LocalDate date,
        @JsonProperty("course_id") UUID courseId
) {
}
