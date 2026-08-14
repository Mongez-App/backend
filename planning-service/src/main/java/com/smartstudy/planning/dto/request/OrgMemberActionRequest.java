package com.smartstudy.planning.dto.request;

import jakarta.validation.constraints.NotBlank;

public record OrgMemberActionRequest(
        @NotBlank String teamId,
        @NotBlank String userId
) {
}
