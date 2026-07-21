package com.smartstudy.planning.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record SessionResponse(
        @JsonProperty("session_id") UUID sessionId,
        @JsonProperty("course_id") UUID courseId,
        @JsonProperty("linked_task_id") UUID linkedTaskId,
        @JsonProperty("started_at") Instant startedAt,
        @JsonProperty("duration_minutes_logged") Integer durationMinutesLogged,
        @JsonProperty("task_completed") Boolean taskCompleted,
        @JsonProperty("alert") AlertResponse alert
) {
}
