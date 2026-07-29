package com.smartstudy.planning.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record RoadmapResponse(
        @JsonProperty("roadmap_start_date") LocalDate roadmapStartDate,
        @JsonProperty("weeks") List<WeekResponse> weeks,
        @JsonProperty("alert") AlertResponse alert
) {

    public record WeekResponse(
            @JsonProperty("week_number") int weekNumber,
            @JsonProperty("start_date") LocalDate startDate,
            @JsonProperty("end_date") LocalDate endDate,
            @JsonProperty("study_blocks") List<StudyBlockResponse> studyBlocks
    ) {
    }

    public record StudyBlockResponse(
            @JsonProperty("block_id") UUID blockId,
            @JsonProperty("course_id") UUID courseId,
            @JsonProperty("course_name") String courseName,
            @JsonProperty("topic") String topic,
            @JsonProperty("duration_minutes") Integer durationMinutes,
            @JsonProperty("is_completed") boolean completed,
            @JsonProperty("events") RoadmapEventResponse events
    ) {
    }

    public record RoadmapEventResponse(
            @JsonProperty("event_id") String eventId,
            @JsonProperty("course_id") UUID courseId,
            @JsonProperty("course_name") String courseName,
            @JsonProperty("title") String title,
            @JsonProperty("event_type") String eventType,
            @JsonProperty("event_date") String eventDate
    ) {
    }
}
