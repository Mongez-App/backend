package com.smartstudy.identity.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PreferencesResponse(
        @JsonProperty("status") String status,
        @JsonProperty("preferences") PreferencesData preferences
) {}
