package com.smartstudy.planning.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public record CreateTeamRequest(
        @NotBlank @JsonProperty("name") String name,
        @JsonProperty("image_url") String imageUrl,
        @JsonProperty("invite_code") String inviteCode
) {
}
