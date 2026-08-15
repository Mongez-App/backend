package com.smartstudy.planning.client;

import com.smartstudy.planning.dto.response.UserPreferencesData;
import com.smartstudy.shared.logging.LoggerFactory;
import org.slf4j.Logger;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

@Component
public class IdentityServiceClientFallbackFactory implements FallbackFactory<IdentityServiceClient> {

    private static final Logger log = LoggerFactory.getLogger(IdentityServiceClientFallbackFactory.class);

    @Override
    public IdentityServiceClient create(Throwable cause) {
        return userId -> {
            // Graceful degradation: null lets UserPreferencesService fall back to the
            // caller-supplied defaults rather than failing the whole request.
            log.warn("Could not load preferences for user {} from identity-service: {}",
                    userId, cause.getMessage());
            return null;
        };
    }
}
