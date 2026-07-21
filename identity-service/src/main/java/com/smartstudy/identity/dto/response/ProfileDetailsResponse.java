package com.smartstudy.identity.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ProfileDetailsResponse(
        @JsonProperty("name") String name,
        @JsonProperty("avatar_url") String avatarUrl,
        @JsonProperty("appearance") String appearance,
        @JsonProperty("language") String language,
        @JsonProperty("calendar_sync_connected") boolean calendarSyncConnected
) {}
