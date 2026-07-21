package com.smartstudy.identity.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;

public record UpdateProfileRequest(
        @JsonProperty("name") String name,
        @JsonProperty("avatar_url") String avatarUrl,
        @JsonProperty("appearance") String appearance,
        @JsonProperty("language") String language,
        @JsonProperty("calendar_sync_connected") Boolean calendarSyncConnected
) {}
