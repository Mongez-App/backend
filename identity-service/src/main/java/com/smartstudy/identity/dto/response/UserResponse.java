package com.smartstudy.identity.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public record UserResponse(
        @JsonProperty("user_id") String userId,
        @JsonProperty("email") String email,
        @JsonProperty("name") String name,
        @JsonProperty("avatar_url") String avatarUrl,
        @JsonProperty("is_guest") boolean isGuest,
        @JsonProperty("appearance") String appearance,
        @JsonProperty("language") String language,
        @JsonProperty("created_at") java.time.Instant createdAt,
        @JsonProperty("updated_at") java.time.Instant updatedAt
) {}
