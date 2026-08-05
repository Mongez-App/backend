package com.smartstudy.identity.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record TeamSearchResponse(
        @JsonProperty("success") boolean success,
        @JsonProperty("data") List<TeamSearchItem> data
) {}