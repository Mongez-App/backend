package com.smartstudy.planning.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

public record TeamEventResponse(
        @JsonProperty("event_id") UUID eventId,
        @JsonProperty("course_name") String courseName,
        @JsonProperty("event_type") String eventType,
        @JsonProperty("due_text") String dueText,
        @JsonProperty("event_date") java.time.Instant eventDate
) {
}
