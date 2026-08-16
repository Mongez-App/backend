package com.smartstudy.planning.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * Body of acceptMember / declineMember: the membership row is addressed by its
 * own id, so the caller does not have to know the team or the member's uid.
 */
public record OrgMemberIdRequest(
        @NotBlank(message = "is required.") String memberId
) {
}
