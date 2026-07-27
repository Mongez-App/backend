package com.smartstudy.planning.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

public record EventResponse(
        @JsonProperty("event_id")
        UUID eventId,
        
        @JsonProperty("course_id")
        UUID courseId,
        
        @JsonProperty("course_name")
        String courseName,
        
        String title,
        
        @JsonProperty("event_type")
        String eventType,
        
        @JsonProperty("event_date")
        Instant eventDate
) {
}
