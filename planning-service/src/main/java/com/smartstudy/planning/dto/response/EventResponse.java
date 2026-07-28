package com.smartstudy.planning.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public record EventResponse(
        @JsonProperty("id") String id,
        @JsonProperty("title") String title,
        @JsonProperty("startDate") String startDate,
        @JsonProperty("endDate") String endDate
) {
}
