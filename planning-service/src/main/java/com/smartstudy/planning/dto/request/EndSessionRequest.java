package com.smartstudy.planning.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record EndSessionRequest(
        @NotNull @JsonProperty("task_completed") Boolean taskCompleted,
        @JsonProperty("completion_time") Instant completionTime
) {
}
