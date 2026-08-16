package com.smartstudy.identity.dto.response;

import java.time.Instant;

public record OrgProfileUpdateResponse(
        String id,
        String name,
        String photoUrl,
        Instant updatedAt
) {
}
