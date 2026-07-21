package com.smartstudy.identity.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ProfileResponse(
        @JsonProperty("user_id") String userId,
        @JsonProperty("name") String name,
        @JsonProperty("email") String email,
        @JsonProperty("avatar_url") String avatarUrl,
        @JsonProperty("stats") ProfileStatsResponse stats
) {}
