package com.smartstudy.planning.dto.response;

import java.util.List;

public record OrgJoinRequestListResponse(
        String teamId,
        List<OrgJoinRequestResponse> requests,
        int total
) {
}
