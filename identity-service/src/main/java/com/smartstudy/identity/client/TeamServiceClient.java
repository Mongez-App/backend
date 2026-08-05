package com.smartstudy.identity.client;

import com.smartstudy.identity.dto.response.CourseTeamResponse;
import com.smartstudy.identity.dto.response.TeamStatsResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.List;

@FeignClient(name = "planning-service", fallbackFactory = TeamServiceClientFallbackFactory.class)
public interface TeamServiceClient {
    @GetMapping("/internal/teams/{teamId}/courses")
    List<CourseTeamResponse> getTeamCourses(
            @PathVariable("teamId") String teamId,
            @RequestHeader("X-User-Id") String userId);

    @GetMapping("/internal/teams/{teamId}/stats")
    TeamStatsResponse getTeamStats(
            @PathVariable("teamId") String teamId,
            @RequestHeader("X-User-Id") String userId);
}