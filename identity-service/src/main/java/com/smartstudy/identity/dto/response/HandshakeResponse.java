package com.smartstudy.identity.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public record HandshakeResponse(
        @JsonProperty("user_id") String userId,
        @JsonProperty("email") String email,
        @JsonProperty("name") String name,
        @JsonProperty("avatar_url") String avatarUrl,
        @JsonProperty("appearance") String appearance,
        @JsonProperty("language") String language,
        @JsonProperty("calendar_sync_connected") boolean calendarSyncConnected,
        @JsonProperty("stats") ProfileStatsResponse stats
) {}
