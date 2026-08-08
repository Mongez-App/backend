package com.smartstudy.planning.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CreateTeamRequest(
        @JsonProperty("name") String name,
        @JsonProperty("image_url") String imageUrl,
        @JsonProperty("invite_code") String inviteCode
) {
}
