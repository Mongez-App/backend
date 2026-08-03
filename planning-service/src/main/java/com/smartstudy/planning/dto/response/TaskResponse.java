package com.smartstudy.planning.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.smartstudy.planning.model.Priority;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TaskResponse(
        @JsonProperty("id") UUID taskId,
        @JsonProperty("user_id") String userId,
        @JsonProperty("course_id") UUID courseId,
        @JsonProperty("title") String title,
        @JsonProperty("duration_minutes") Integer durationMinutes,
        @JsonProperty("priority") Priority priority,
        @JsonProperty("completed") boolean isCompleted,
        @JsonProperty("scheduled_date") LocalDate date,
        @JsonProperty("sequence_order") Integer sequenceOrder,
        @JsonProperty("created_at") Instant createdAt
) {
}
