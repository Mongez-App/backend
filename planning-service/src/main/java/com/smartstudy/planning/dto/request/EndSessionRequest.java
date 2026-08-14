package com.smartstudy.planning.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.Instant;

public record EndSessionRequest(
        @NotNull @JsonProperty("task_completed") Boolean taskCompleted,
        @JsonProperty("completion_time") Instant completionTime,
        @PositiveOrZero @JsonProperty("active_spent_time") Integer activeSpentTime
) {
}
