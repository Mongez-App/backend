package com.smartstudy.planning.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record CreateCourseRequest(
        @NotBlank @JsonProperty("name") String name,
        @JsonProperty("course_code") String courseCode,
        @JsonProperty("image_url") String imageUrl,
        @NotNull @JsonProperty("start_date") Instant startDate,
        @JsonProperty("exam_date") Instant examDate,
        @JsonProperty("has_materials") Boolean hasMaterials
) {
}
