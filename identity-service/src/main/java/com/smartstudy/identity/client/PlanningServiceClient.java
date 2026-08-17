package com.smartstudy.identity.client;

import com.smartstudy.identity.dto.response.ProfileStatsResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "planning-service", fallbackFactory = PlanningServiceClientFallbackFactory.class)
public interface PlanningServiceClient {
    @GetMapping("/internal/users/{uid}/stats")
    ProfileStatsResponse getUserStats(@PathVariable("uid") String uid);

    @PostMapping(path = "/roadmap/reschedule", consumes = MediaType.APPLICATION_JSON_VALUE)
    void rescheduleRoadmap(
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-Daily-Study-Minutes") int dailyStudyMinutes,
            @RequestHeader("X-Preferred-Days") String preferredDays
    );
}
