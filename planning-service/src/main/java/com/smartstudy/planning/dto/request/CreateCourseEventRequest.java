package com.smartstudy.planning.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

/**
 * Body of POST /courses/{course_id}/events — a user-created milestone event.
 * Distinct from {@link CreateEventRequest}, which backs the calendar import endpoint.
 */
public record CreateCourseEventRequest(
        @NotBlank @JsonProperty("title") String title,
        @NotBlank @JsonProperty("event_type") String eventType,
        @NotBlank @JsonProperty("event_date") String eventDate
) {
}
