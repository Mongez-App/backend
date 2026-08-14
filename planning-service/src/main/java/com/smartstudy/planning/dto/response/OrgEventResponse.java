package com.smartstudy.planning.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record OrgEventResponse(
        UUID id,
        String teamId,
        UUID courseId,
        String courseName,
        String eventType,
        LocalDate eventDate,
        Long daysLeft,
        Instant createdAt
) {
}
