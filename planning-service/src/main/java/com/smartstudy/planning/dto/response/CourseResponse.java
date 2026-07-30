package com.smartstudy.planning.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.smartstudy.planning.model.CourseType;

import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CourseResponse(
        @JsonProperty("id") UUID id,
        @JsonProperty("user_id") String userId,
        @JsonProperty("name") String name,
        @JsonProperty("course_code") String courseCode,
        @JsonProperty("image_url") String imageUrl,
        @JsonProperty("start_date") Instant startDate,
        @JsonProperty("exam_date") Instant examDate,
        @JsonProperty("course_type") CourseType courseType,
        @JsonProperty("material_url") String materialUrl,
        @JsonProperty("is_hidden") Boolean hidden,
        @JsonProperty("completion_percentage") double completionPercentage,
        @JsonProperty("alert") AlertResponse alert
) {
}
