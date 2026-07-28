package com.smartstudy.identity.client;

import com.smartstudy.identity.dto.response.ProfileStatsResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "planning-service", fallbackFactory = PlanningServiceClientFallbackFactory.class)
public interface PlanningServiceClient {
    @GetMapping("/internal/users/{uid}/stats")
    ProfileStatsResponse getUserStats(@PathVariable("uid") String uid);
}
