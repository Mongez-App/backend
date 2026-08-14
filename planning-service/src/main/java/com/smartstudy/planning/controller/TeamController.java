package com.smartstudy.planning.controller;

import com.smartstudy.planning.dto.request.CreateTeamRequest;
import com.smartstudy.planning.dto.request.JoinTeamRequest;
import com.smartstudy.planning.dto.response.*;
import com.smartstudy.planning.service.TeamService;
import com.smartstudy.shared.exception.BadRequestException;
import com.smartstudy.shared.logging.LoggerFactory;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController()
@RequestMapping("/organization")
@RequiredArgsConstructor
public class TeamController {

    private static final Logger log = LoggerFactory.getLogger(TeamController.class);
    private final TeamService teamService;

    @GetMapping("/{orgId}/teams")
    public List<TeamResponse> getTeams(
            @PathVariable String orgId,
            @RequestHeader("X-User-Id") String userId) {
        log.info("Incoming request: GET /organization/{}/teams | userId: {}", orgId, userId);
        validateUserId(userId);
        return teamService.getUserTeams(userId, orgId);
    }

    @GetMapping("/{orgId}/teams/{teamId}/courses")
    public List<TeamCourseResponse> getTeamCourses(
            @PathVariable String orgId,
            @PathVariable String teamId,
            @RequestHeader("X-User-Id") String userId) {
        log.info("Incoming request: GET /organization/{}/teams/{}/courses | userId: {}", orgId, teamId, userId);
        validateUserId(userId);
        return teamService.getTeamCourses(teamId, userId, orgId);
    }

    @GetMapping("/{orgId}/team/{teamId}/events")
    public List<TeamEventResponse> getTeamEvents(
            @PathVariable String orgId,
            @PathVariable String teamId,
            @RequestHeader("X-User-Id") String userId) {
        log.info("Incoming request: GET /organization/{}/team/{}/events | userId: {}", orgId, teamId, userId);
        validateUserId(userId);
        return teamService.getTeamEvents(teamId, userId, orgId);
    }

    @GetMapping("/{orgId}/teams/discover")
    public DiscoverResponse discover(
            @PathVariable String orgId,
            @RequestHeader("X-User-Id") String userId) {
        log.info("Incoming request: GET /organization/{}/teams/discover | userId: {}", orgId, userId);
        validateUserId(userId);
        return teamService.discover(userId, orgId);
    }

    @GetMapping("/{orgId}/teams/search")
    public List<SearchTeamResponse> searchTeams(
            @PathVariable String orgId,
            @RequestParam String q,
            @RequestHeader("X-User-Id") String userId) {
        log.info("Incoming request: GET /organization/{}/teams/search | q: {} | userId: {}", orgId, q, userId);
        validateUserId(userId);
        if (q == null || q.isBlank()) {
            throw new BadRequestException("MISSING_QUERY", "Search query parameter 'q' is required.");
        }
        return teamService.searchTeams(q, userId, orgId);
    }

    @PostMapping("/{orgId}/teams/join")
    public JoinTeamResponse joinTeam(
            @PathVariable String orgId,
            @Valid @RequestBody JoinTeamRequest request,
            @RequestHeader("X-User-Id") String userId) {
        log.info("Incoming request: POST /organization/{}/teams/join | userId: {}", orgId, userId);
        validateUserId(userId);
        return teamService.joinTeam(userId, orgId, request.inviteCode());
    }

    @PostMapping("/{orgId}/teams")
    @ResponseStatus(HttpStatus.CREATED)
    public TeamResponse createTeam(
            @PathVariable String orgId,
            @Valid @RequestBody CreateTeamRequest request,
            @RequestHeader("X-User-Id") String userId) {
        log.info("Incoming request: POST /organization/{}/teams | userId: {}", orgId, userId);
        validateUserId(userId);
        return teamService.createTeam(userId, orgId, request);
    }

    private void validateUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new BadRequestException("MISSING_USER_ID", "X-User-Id header is required.");
        }
    }
}
