package com.smartstudy.planning.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

public record UpdateCourseRequest(
        @JsonProperty("name") String name,
        @JsonProperty("course_code") String courseCode,
        @JsonProperty("image_url") String imageUrl,
        @JsonProperty("start_date") Instant startDate,
        @JsonProperty("exam_date") Instant examDate,
        @JsonProperty("has_materials") Boolean hasMaterials,
        @JsonProperty("is_hidden") Boolean hidden
) {
}
