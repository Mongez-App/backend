package com.smartstudy.planning.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public record CreateEventRequest(
        @NotBlank @JsonProperty("title") String title,
        @NotBlank @JsonProperty("startDate") String startDate,
        @NotBlank @JsonProperty("endDate") String endDate,
        @JsonProperty("event_type") String eventType,
        @JsonProperty("can_study_through") boolean canStudyThrough
) {
}
