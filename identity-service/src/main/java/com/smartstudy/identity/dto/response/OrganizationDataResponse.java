package com.smartstudy.identity.dto.response;

import java.time.Instant;

public record OrganizationDataResponse(
        String uid,
        String email,
        String name,
        String avatar,
        String description,
        String established_at,
        Integer no_of_students,
        Integer no_of_courses,
        Integer no_of_teams
) {
}
