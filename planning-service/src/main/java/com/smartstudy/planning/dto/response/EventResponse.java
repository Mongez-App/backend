package com.smartstudy.planning.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

public record EventResponse(
        @JsonProperty("id") String id,
        @JsonProperty("title") String title,
        @JsonProperty("startDate") String startDate,
        @JsonProperty("endDate") String endDate,
        @JsonProperty("course_id") UUID courseId,
        @JsonProperty("course_name") String courseName,
        @JsonProperty("can_study_through") boolean canStudyThrough,
        @JsonProperty("system_event") boolean systemEvent
) {
}
