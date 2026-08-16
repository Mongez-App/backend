package com.smartstudy.identity.dto.response;

/**
 * Minimal user record served to sibling services (planning-service resolves
 * team-member names with it). Deliberately narrower than {@link UserResponse}:
 * it carries no preferences, stats, or calendar state.
 */
public record InternalUserSummaryResponse(
        String id,
        String name,
        String email
) {
}
