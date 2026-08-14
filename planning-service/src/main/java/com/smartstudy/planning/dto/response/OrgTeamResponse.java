package com.smartstudy.planning.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record OrgTeamResponse(
        String id,
        String name,
        String photoUrl,
        String ownerId,
        Long memberCount,
        Integer progress,
        List<String> events,
        Instant createdAt,
        Instant updatedAt
) {
}
