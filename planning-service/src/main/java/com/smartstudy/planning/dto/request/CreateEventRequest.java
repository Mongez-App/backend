package com.smartstudy.planning.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record CreateEventRequest(
        @NotBlank
        String title,

        @NotBlank
        @JsonProperty("event_type")
        String eventType,

        @NotNull
        @JsonProperty("event_date")
        Instant eventDate
) {
}
