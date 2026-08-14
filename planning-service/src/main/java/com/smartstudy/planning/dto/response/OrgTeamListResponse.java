package com.smartstudy.planning.dto.response;

import java.util.List;

public record OrgTeamListResponse(
        List<OrgTeamResponse> teams,
        int total
) {
}
