package com.smartstudy.planning.controller;

import com.smartstudy.planning.dto.request.JoinTeamRequest;
import com.smartstudy.planning.dto.response.*;
import com.smartstudy.planning.service.TeamService;
import com.smartstudy.shared.exception.BadRequestException;
import com.smartstudy.shared.logging.LoggerFactory;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/teams")
@RequiredArgsConstructor
public class UserTeamController {

    private static final Logger log = LoggerFactory.getLogger(UserTeamController.class);
    private final TeamService teamService;

    @GetMapping
    public List<TeamResponse> getUserTeams(
            @RequestHeader("X-User-Id") String userId) {
        log.info("Incoming request: GET /teams | userId: {}", userId);
        validateUserId(userId);
        return teamService.getUserTeams(userId);
    }

    @GetMapping("/{teamId}/courses")
    public List<TeamCourseResponse> getTeamCourses(
            @PathVariable String teamId,
            @RequestHeader("X-User-Id") String userId) {
        log.info("Incoming request: GET /teams/{}/courses | userId: {}", teamId, userId);
        validateUserId(userId);
        return teamService.getTeamCourses(teamId, userId);
    }

    @GetMapping("/team/{teamId}/events")
    public List<TeamEventResponse> getTeamEvents(
            @PathVariable String teamId,
            @RequestHeader("X-User-Id") String userId) {
        log.info("Incoming request: GET /team/{}/events | userId: {}", teamId, userId);
        validateUserId(userId);
        return teamService.getTeamEvents(teamId, userId);
    }

    @GetMapping("/discover")
    public DiscoverResponse discover(
            @RequestHeader("X-User-Id") String userId) {
        log.info("Incoming request: GET /teams/discover | userId: {}", userId);
        validateUserId(userId);
        return teamService.discover(userId);
    }

    @GetMapping("/search")
    public SearchTeamsResponse searchTeams(
            @RequestParam String q,
            @RequestHeader("X-User-Id") String userId) {
        log.info("Incoming request: GET /teams/search | q: {} | userId: {}", q, userId);
        validateUserId(userId);
        if (q == null || q.isBlank()) {
            throw new BadRequestException("MISSING_QUERY", "Search query parameter 'q' is required.");
        }
        List<SearchTeamResponse> data = teamService.searchTeams(q, userId);
        return new SearchTeamsResponse(true, data);
    }

    @PostMapping("/join")
    public JoinTeamApiResponse joinTeam(
            @Valid @RequestBody JoinTeamRequest request,
            @RequestHeader("X-User-Id") String userId) {
        log.info("Incoming request: POST /teams/join | userId: {}", userId);
        validateUserId(userId);
        JoinTeamResponse data = teamService.joinTeam(userId, request.inviteCode());
        return new JoinTeamApiResponse(true, data, data.message());
    }

    private void validateUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new BadRequestException("MISSING_USER_ID", "X-User-Id header is required.");
        }
    }
}
