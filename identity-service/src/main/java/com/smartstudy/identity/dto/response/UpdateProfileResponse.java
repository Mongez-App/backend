package com.smartstudy.identity.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public record UpdateProfileResponse(
        @JsonProperty("status") String status,
        @JsonProperty("profile") ProfileDetailsResponse profile
) {}
