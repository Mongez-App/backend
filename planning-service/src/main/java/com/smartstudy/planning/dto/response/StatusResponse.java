package com.smartstudy.planning.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public record StatusResponse(
        @JsonProperty("status") String status,
        @JsonProperty("alert") AlertResponse alert
) {
}
