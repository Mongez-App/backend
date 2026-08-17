package com.smartstudy.planning.dto.response;

/**
 * Mirrors identity-service's {@code InternalOrganizationSummaryResponse}. Teams
 * live here, but the organization's name lives in identity-service, so it has
 * to be asked for and copied onto the team when the team is created.
 */
public record OrganizationSummaryData(
        String id,
        String name
) {
}
