package com.smartstudy.planning.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TeamEventSummary(
        @JsonProperty("event_type") String eventType
) {}