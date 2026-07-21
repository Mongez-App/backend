package com.smartstudy.identity.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CreateUserRequest(
        @JsonProperty("email") String email,
        @JsonProperty("name") String name,
        @JsonProperty("is_guest") Boolean isGuest
) {}
