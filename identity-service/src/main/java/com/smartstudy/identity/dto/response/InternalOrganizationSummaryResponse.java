package com.smartstudy.identity.dto.response;

/**
 * Minimal organization record served to sibling services (planning-service
 * stamps the name onto teams and courses when they are created). Deliberately
 * narrower than {@link OrgProfileResponse}: it carries no counters, contact
 * details, or timestamps.
 */
public record InternalOrganizationSummaryResponse(
        String id,
        String name
) {
}
