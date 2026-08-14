package com.smartstudy.planning.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record OrgCourseResponse(
        UUID id,
        String teamId,
        String name,
        LocalDate startDate,
        LocalDate endDate,
        String thumbnailUrl,
        Integer progress,
        Long materialCount,
        Instant createdAt
) {
}
