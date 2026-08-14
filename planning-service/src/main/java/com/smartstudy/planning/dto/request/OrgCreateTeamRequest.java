package com.smartstudy.planning.dto.request;

import jakarta.validation.constraints.NotBlank;

public record OrgCreateTeamRequest(
        @NotBlank String name,
        String photoUrl
) {
}
