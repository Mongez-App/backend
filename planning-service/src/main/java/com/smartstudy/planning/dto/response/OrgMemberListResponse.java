package com.smartstudy.planning.dto.response;

import java.util.List;

public record OrgMemberListResponse(
        String teamId,
        List<OrgMemberResponse> pendingMembers,
        List<OrgMemberResponse> teamMembers,
        int pendingTotal,
        int teamTotal
) {
}
