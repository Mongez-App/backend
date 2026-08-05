package com.smartstudy.planning.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

public record CourseEventResponse(
        @JsonProperty("event_id") String eventId,
        @JsonProperty("event_title") String eventTitle,
        @JsonProperty("event_type") String eventType,
        @JsonProperty("event_date") Instant eventDate
) {}