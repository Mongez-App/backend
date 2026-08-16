package com.smartstudy.identity.dto.response;

/**
 * Organization profile as the mobile Profile screen reads it. {@code email} is
 * read-only here — it comes from the Firebase account the organization
 * registered with.
 */
public record OrgProfileResponse(
        String id,
        String name,
        String photoUrl,
        String email
) {
}
