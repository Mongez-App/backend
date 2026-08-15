package com.smartstudy.planning.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record TasksMetaResponse(
        @JsonProperty("meta") TasksMeta meta,
        @JsonProperty("data") List<TaskResponse> data
) {
}
