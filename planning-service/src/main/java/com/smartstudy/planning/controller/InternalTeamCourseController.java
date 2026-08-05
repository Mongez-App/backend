package com.smartstudy.planning.controller;

import com.smartstudy.planning.dto.response.CourseTeamResponse;
import com.smartstudy.planning.dto.response.TeamStatsResponse;
import com.smartstudy.planning.service.TeamCourseService;
import com.smartstudy.shared.logging.LoggerFactory;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/internal/teams")
@RequiredArgsConstructor
public class InternalTeamCourseController {

    private static final Logger log = LoggerFactory.getLogger(InternalTeamCourseController.class);
    private final TeamCourseService teamCourseService;

    @GetMapping("/{teamId}/courses")
    public List<CourseTeamResponse> getTeamCourses(
            @PathVariable String teamId,
            @RequestHeader("X-User-Id") String userId) {
        log.info("Internal request: GET /internal/teams/{}/courses | userId: {}", teamId, userId);
        return teamCourseService.getTeamCourses(teamId, userId);
    }

    @GetMapping("/{teamId}/stats")
    public TeamStatsResponse getTeamStats(
            @PathVariable String teamId,
            @RequestHeader("X-User-Id") String userId) {
        log.info("Internal request: GET /internal/teams/{}/stats | userId: {}", teamId, userId);
        return teamCourseService.getTeamStats(teamId, userId);
    }
}