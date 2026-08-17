package com.smartstudy.planning.service;

import com.smartstudy.planning.client.IdentityServiceClient;
import com.smartstudy.planning.dto.response.OrganizationSummaryData;
import com.smartstudy.shared.logging.LoggerFactory;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Resolves an organization id to its display name.
 *
 * Teams and courses each keep a denormalized {@code organizationName}, but the
 * name itself lives in identity-service's {@code organizations} table — another
 * service and another database. The gateway only forwards the organization's
 * uid as {@code X-User-Id}, so the name has to be fetched, and the copy is
 * written once at creation: this is the only chance to get it right.
 */
@Component
@RequiredArgsConstructor
public class OrganizationNameResolver {

    private static final Logger log = LoggerFactory.getLogger(OrganizationNameResolver.class);

    private final IdentityServiceClient identityServiceClient;

    /**
     * @param organizationId the organization's uid, which is also the caller's
     *                       {@code X-User-Id} on the org-admin API
     * @return the organization's display name, or null when identity-service
     *         has no organization under that id or the name is unset
     * @throws com.smartstudy.planning.exception.OrganizationLookupUnavailableException
     *         when identity-service could not be reached, so callers fail
     *         rather than persist a null they cannot distinguish later
     */
    public String resolve(String organizationId) {
        List<OrganizationSummaryData> found =
                identityServiceClient.lookupOrganizations(organizationId, List.of(organizationId));

        if (found == null || found.isEmpty()) {
            // identity-service answered, and its answer is "no such organization".
            // Regular users creating a team hit this: their orgId is not itself an
            // organization account. Not an error — just nothing to stamp.
            log.warn("identity-service has no organization {}; leaving organizationName unset", organizationId);
            return null;
        }

        String name = found.getFirst().name();
        if (name == null || name.isBlank()) {
            log.warn("Organization {} has no name set; leaving organizationName unset", organizationId);
            return null;
        }
        return name;
    }
}
