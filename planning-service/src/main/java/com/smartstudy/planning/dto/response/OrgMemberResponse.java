package com.smartstudy.planning.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * One row of the Members tab. Pending rows carry {@code requestedAt} and active
 * rows {@code joinedAt}; the unused one is omitted rather than sent as null.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OrgMemberResponse(
        String id,
        String name,
        String avatarInitials,
        String avatarColor,
        String status,
        Instant requestedAt,
        Instant joinedAt
) {
}
