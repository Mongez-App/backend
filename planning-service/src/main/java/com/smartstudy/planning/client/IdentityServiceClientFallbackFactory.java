package com.smartstudy.planning.client;

import com.smartstudy.planning.dto.response.OrganizationSummaryData;
import com.smartstudy.planning.dto.response.UserPreferencesData;
import com.smartstudy.planning.dto.response.UserSummaryData;
import com.smartstudy.planning.exception.OrganizationLookupUnavailableException;
import com.smartstudy.shared.logging.LoggerFactory;
import org.slf4j.Logger;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class IdentityServiceClientFallbackFactory implements FallbackFactory<IdentityServiceClient> {

    private static final Logger log = LoggerFactory.getLogger(IdentityServiceClientFallbackFactory.class);

    @Override
    public IdentityServiceClient create(Throwable cause) {
        return new IdentityServiceClient() {

            @Override
            public UserPreferencesData getPreferences(String userId) {
                // Graceful degradation: null lets UserPreferencesService fall back to the
                // caller-supplied defaults rather than failing the whole request.
                log.warn("Could not load preferences for user {} from identity-service: {}",
                        userId, cause.getMessage());
                return null;
            }

            @Override
            public List<UserSummaryData> lookupUsers(String callerId, List<String> ids) {
                // The Members tab still renders with placeholder names rather than
                // failing outright when identity-service is unreachable.
                log.warn("Could not resolve {} member name(s) from identity-service: {}",
                        ids == null ? 0 : ids.size(), cause.getMessage());
                return List.of();
            }

            @Override
            public List<OrganizationSummaryData> lookupOrganizations(String callerId, List<String> ids) {
                // No graceful degradation here, unlike the two above: the caller is
                // about to persist whatever comes back into a column it never
                // rewrites, so an empty list would bake in a permanent null.
                log.error("Could not resolve {} organization name(s) from identity-service: {}",
                        ids == null ? 0 : ids.size(), cause.getMessage());
                throw new OrganizationLookupUnavailableException(
                        "identity-service is unreachable, so the organization name could not be resolved.",
                        cause);
            }
        };
    }
}
