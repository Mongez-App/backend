package com.smartstudy.planning.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

public record CourseTeamResponse(
        @JsonProperty("course_id") UUID courseId,
        @JsonProperty("team_id") String teamId,
        @JsonProperty("user_id") String userId,
        @JsonProperty("name") String name,
        @JsonProperty("course_code") String courseCode,
        @JsonProperty("start_date") Instant startDate,
        @JsonProperty("exam_date") Instant examDate,
        @JsonProperty("course_image_url") String courseImageUrl,
        @JsonProperty("completion_percentage") double completionPercentage
) {}