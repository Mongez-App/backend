package com.smartstudy.planning.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * Result of accepting or declining a join request. {@code joinedAt} is only
 * present on acceptance.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OrgMemberActionResponse(
        String id,
        String teamId,
        String status,
        Instant joinedAt
) {
}
