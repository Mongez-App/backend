package com.smartstudy.planning.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

public record TeamCourseResponse(
        @JsonProperty("course_id") UUID courseId,
        @JsonProperty("team_id") String teamId,
        @JsonProperty("user_id") String userId,
        @JsonProperty("name") String name,
        @JsonProperty("course_code") String courseCode,
        @JsonProperty("start_date") java.time.Instant startDate,
        @JsonProperty("end_date") java.time.Instant endDate,
        @JsonProperty("course_image_url") String courseImageUrl,
        @JsonProperty("completion_percentage") double completionPercentage
) {
}
