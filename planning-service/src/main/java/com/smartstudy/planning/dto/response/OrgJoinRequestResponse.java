package com.smartstudy.planning.dto.response;

import java.time.Instant;

public record OrgJoinRequestResponse(
        String userId,
        Instant appliedAt
) {
}
