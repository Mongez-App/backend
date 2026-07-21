package com.smartstudy.identity.client;

import com.smartstudy.identity.dto.request.CalendarConnectRequest;
import com.smartstudy.identity.dto.response.CalendarConnectResponse;
import com.smartstudy.identity.dto.response.CalendarStatusResponse;
import com.smartstudy.identity.dto.response.ProfileStatsResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "planning-service", fallbackFactory = PlanningServiceClientFallbackFactory.class)
public interface PlanningServiceClient {
    @GetMapping("/internal/users/{uid}/stats")
    ProfileStatsResponse getUserStats(@PathVariable("uid") String uid);

    @GetMapping("/auth/calendar/status")
    CalendarStatusResponse getCalendarStatus(@RequestHeader("X-User-Id") String userId);

    @PostMapping("/auth/calendar/connect")
    CalendarConnectResponse connectCalendar(@RequestHeader("X-User-Id") String userId, @RequestBody CalendarConnectRequest request);

    @DeleteMapping("/auth/calendar/disconnect")
    void disconnectCalendar(@RequestHeader("X-User-Id") String userId);
}
