package com.smartstudy.planning.dto.response;

/**
 * Mirrors identity-service's {@code InternalUserSummaryResponse}. Team
 * membership lives here but the person's name and email live in
 * identity-service, so the Members tab has to ask for them.
 */
public record UserSummaryData(
        String id,
        String name,
        String email
) {
}
