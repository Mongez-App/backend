package com.smartstudy.planning.dto.response;

public record OrgMemberStatusResponse(
        String teamId,
        String userId,
        String status
) {
}
