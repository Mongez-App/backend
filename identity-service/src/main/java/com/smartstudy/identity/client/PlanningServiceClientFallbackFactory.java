package com.smartstudy.identity.client;

import com.smartstudy.identity.dto.request.CalendarConnectRequest;
import com.smartstudy.identity.dto.response.CalendarConnectResponse;
import com.smartstudy.identity.dto.response.CalendarStatusResponse;
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

            @Override
            public CalendarStatusResponse getCalendarStatus(String userId) {
                return new CalendarStatusResponse(false, null, null);
            }

            @Override
            public CalendarConnectResponse connectCalendar(String userId, CalendarConnectRequest request) {
                throw new com.smartstudy.shared.exception.BadRequestException("SERVICE_UNAVAILABLE", "Calendar service is currently unavailable.");
            }

            @Override
            public void disconnectCalendar(String userId) {
                // no-op: calendar service is unavailable
            }
        };
    }
}
