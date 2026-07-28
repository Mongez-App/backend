package com.smartstudy.identity.client;

import com.smartstudy.identity.dto.response.ProfileStatsResponse;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

@Component
public class PlanningServiceClientFallbackFactory implements FallbackFactory<PlanningServiceClient> {
    @Override
    public PlanningServiceClient create(Throwable cause) {
        return new PlanningServiceClient() {
            @Override
            public ProfileStatsResponse getUserStats(String uid) {
                // Graceful degradation: return 0s if planning-service is down or endpoint doesn't exist
                return new ProfileStatsResponse(0, 0, 0);
            }
        };
    }
}
