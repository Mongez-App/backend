package com.smartstudy.identity.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

public record HandshakeResponse(
        @JsonProperty("user_id") String userId,
        @JsonProperty("email") String email,
        @JsonProperty("name") String name,
        @JsonProperty("is_new_user") boolean isNewUser,
        @JsonProperty("created_at") Instant createdAt
) {}
