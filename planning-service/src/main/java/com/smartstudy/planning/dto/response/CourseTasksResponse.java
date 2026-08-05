package com.smartstudy.planning.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record CourseTasksResponse(
        @JsonProperty("meta") CourseTasksMeta meta,
        @JsonProperty("data") List<TaskResponse> data
) {}