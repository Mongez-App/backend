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
                return new ProfileStatsResponse(0, 0, 0);
            }

            @Override
            public void rescheduleRoadmap(String userId, int dailyStudyMinutes, String preferredDays) {
                // Graceful degradation: skip reschedule if planning-service is unavailable
            }
        };
    }
}
