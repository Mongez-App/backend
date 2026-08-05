package com.smartstudy.identity.controller;

import com.smartstudy.identity.dto.request.JoinTeamRequest;
import com.smartstudy.identity.dto.response.DiscoverResponse;
import com.smartstudy.identity.dto.response.JoinTeamResponse;
import com.smartstudy.identity.dto.response.TeamResponse;
import com.smartstudy.identity.dto.response.TeamSearchResponse;
import com.smartstudy.identity.service.TeamService;
import com.smartstudy.shared.logging.LoggerFactory;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/teams")
@RequiredArgsConstructor
public class TeamController {

    private static final Logger log = LoggerFactory.getLogger(TeamController.class);
    private final TeamService teamService;

    @GetMapping
    public List<TeamResponse> getTeams(@RequestHeader("X-User-Id") String userId) {
        log.info("Incoming request: GET /teams | userId: {}", userId);
        return teamService.getUserTeams(userId);
    }

    @GetMapping("/{teamId}/courses")
    public List<com.smartstudy.identity.dto.response.CourseTeamResponse> getTeamCourses(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable String teamId) {
        log.info("Incoming request: GET /teams/{}/courses | userId: {}", teamId, userId);
        // This will call the Feign client to planning-service
        return teamService.getTeamCourses(userId, teamId);
    }

    @GetMapping("/discover")
    public DiscoverResponse getDiscover(@RequestHeader("X-User-Id") String userId) {
        log.info("Incoming request: GET /teams/discover | userId: {}", userId);
        return teamService.getDiscoverData(userId);
    }

    @GetMapping("/search")
    public TeamSearchResponse searchTeams(
            @RequestHeader("X-User-Id") String userId,
            @RequestParam String q) {
        log.info("Incoming request: GET /teams/search | userId: {} | query: {}", userId, q);
        return teamService.searchTeams(userId, q);
    }

    @PostMapping("/join")
    public ResponseEntity<JoinTeamResponse> joinTeam(
            @RequestHeader("X-User-Id") String userId,
            @Valid @RequestBody JoinTeamRequest request) {
        log.info("Incoming request: POST /teams/join | userId: {}", userId);
        JoinTeamResponse response = teamService.joinTeam(userId, request);
        return ResponseEntity.ok(response);
    }
}