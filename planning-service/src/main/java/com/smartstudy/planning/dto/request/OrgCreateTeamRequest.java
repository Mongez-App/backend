package com.smartstudy.planning.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

/**
 * {@code invite_code} is optional: supply one to choose the code students will
 * be given — admins hand it out off-platform, so it has to be a value they
 * picked — or leave it out and the server generates one.
 * <p>
 * Named to match the student-side join payload rather than the camelCase of
 * the surrounding org API: one concept, one name. The camelCase spelling is
 * accepted as an alias.
 * </p>
 */
public record OrgCreateTeamRequest(
        @NotBlank String name,
        String photoUrl,
        @JsonProperty("invite_code") @JsonAlias("inviteCode") String inviteCode
) {
}
