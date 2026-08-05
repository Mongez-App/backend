package com.smartstudy.identity.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public record JoinTeamRequest(
        @NotBlank @JsonProperty("inviteCode") String inviteCode
) {}