package com.smartstudy.planning.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

public record JoinTeamRequest(
        @JsonProperty("invite_code") @JsonAlias("inviteCode") String inviteCode
) {
}
