package com.smartstudy.identity.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;

public record OrganizationUpdateRequest(
        @JsonProperty("name") String name,
        @JsonProperty("description") String description,
        @JsonProperty("avatar_url") String avatarUrl
) {
}
