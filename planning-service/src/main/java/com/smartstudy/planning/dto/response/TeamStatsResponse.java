package com.smartstudy.planning.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record TeamStatsResponse(
        @JsonProperty("completion_percentage") double completionPercentage,
        @JsonProperty("events") List<TeamEventSummary> events
) {}