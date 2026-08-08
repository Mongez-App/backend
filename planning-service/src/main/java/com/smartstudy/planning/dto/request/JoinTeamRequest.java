package com.smartstudy.planning.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;

public record JoinTeamRequest(
        @JsonProperty("inviteCode") String inviteCode
) {
}
