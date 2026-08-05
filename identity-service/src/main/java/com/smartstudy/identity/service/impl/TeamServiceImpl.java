package com.smartstudy.identity.service.impl;

import com.smartstudy.identity.client.TeamServiceClient;
import com.smartstudy.identity.dto.response.CourseTeamResponse;
import com.smartstudy.identity.dto.response.DiscoverResponse;
import com.smartstudy.identity.dto.response.JoinTeamResponse;
import com.smartstudy.identity.dto.response.PendingRequestResponse;
import com.smartstudy.identity.dto.response.TeamResponse;
import com.smartstudy.identity.dto.response.TeamSearchItem;
import com.smartstudy.identity.dto.response.TeamSearchResponse;
import com.smartstudy.identity.dto.response.TeamStatsResponse;
import com.smartstudy.identity.dto.response.TrendingTeamResponse;
import com.smartstudy.identity.dto.response.JoinTeamData;
import com.smartstudy.identity.dto.request.JoinTeamRequest;
import com.smartstudy.identity.model.JoinRequest;
import com.smartstudy.identity.model.JoinRequestStatus;
import com.smartstudy.identity.model.Team;
import com.smartstudy.identity.model.TeamMembership;
import com.smartstudy.identity.repository.JoinRequestRepository;
import com.smartstudy.identity.repository.TeamMembershipRepository;
import com.smartstudy.identity.repository.TeamRepository;
import com.smartstudy.identity.service.TeamService;
import com.smartstudy.shared.exception.ConflictException;
import com.smartstudy.shared.exception.NotFoundException;
import com.smartstudy.shared.logging.LoggerFactory;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Random;

@Service
@RequiredArgsConstructor
public class TeamServiceImpl implements TeamService {

    private static final Logger log = LoggerFactory.getLogger(TeamServiceImpl.class);
    private static final String INVITE_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final int INVITE_CODE_LENGTH = 8;

    private final TeamRepository teamRepository;
    private final TeamMembershipRepository teamMembershipRepository;
    private final JoinRequestRepository joinRequestRepository;
    private final TeamServiceClient teamServiceClient;

    @Override
    @Transactional(readOnly = true)
    public List<TeamResponse> getUserTeams(String userId) {
        log.info("Fetching teams for userId: {}", userId);
        List<TeamMembership> memberships = teamMembershipRepository.findByUserId(userId);

        return memberships.stream().map(membership -> {
            Team team = membership.getTeam();
            TeamStatsResponse stats = getTeamStatsFromPlanning(team.getId(), userId);

            String orgName = team.getOrganization() != null ? team.getOrganization().getName() : "Unknown Organization";

            return new TeamResponse(
                    team.getId(),
                    team.getName(),
                    orgName,
                    team.getImageUrl(),
                    stats != null ? stats.completionPercentage() : 0.0,
                    stats != null ? stats.events() : List.of()
            );
        }).toList();
    }

    private TeamStatsResponse getTeamStatsFromPlanning(String teamId, String userId) {
        try {
            return teamServiceClient.getTeamStats(teamId, userId);
        } catch (Exception e) {
            log.warn("Failed to fetch team stats from planning-service for team {}: {}", teamId, e.getMessage());
            return null;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public DiscoverResponse getDiscoverData(String userId) {
        log.info("Fetching discover data for userId: {}", userId);

        // Pending requests
        List<JoinRequest> pendingRequests = joinRequestRepository.findByUserId(userId)
                .stream()
                .filter(jr -> jr.getStatus() == JoinRequestStatus.PENDING)
                .toList();

        List<PendingRequestResponse> pendingResponses = pendingRequests.stream()
                .map(jr -> new PendingRequestResponse(
                        jr.getTeam().getId(),
                        jr.getTeam().getName(),
                        jr.getTeam().getImageUrl(),
                        jr.getTeam().getOrganization() != null ? jr.getTeam().getOrganization().getName() : "Unknown",
                        jr.getAppliedAt(),
                        jr.getStatus().name().toLowerCase()
                ))
                .toList();

        // Trending teams (public teams user is not a member of)
        List<Team> userTeams = teamMembershipRepository.findByUserId(userId)
                .stream()
                .map(TeamMembership::getTeam)
                .toList();

        List<Team> allPublicTeams = teamRepository.findPublicTeams(PageRequest.of(0, 20)).getContent();

        List<TrendingTeamResponse> trendingResponses = allPublicTeams.stream()
                .filter(team -> userTeams.stream().noneMatch(ut -> ut.getId().equals(team.getId())))
                .limit(10)
                .map(team -> new TrendingTeamResponse(
                        team.getId(),
                        team.getName(),
                        team.getImageUrl(),
                        team.getOrganization() != null ? team.getOrganization().getName() : "Unknown",
                        teamMembershipRepository.findByTeamId(team.getId()).size(),
                        team.getDescription() != null ? team.getDescription() : "No description available"
                ))
                .toList();

        return new DiscoverResponse(pendingResponses, trendingResponses);
    }

    @Override
    @Transactional(readOnly = true)
    public TeamSearchResponse searchTeams(String userId, String query) {
        log.info("Searching teams for userId: {} with query: {}", userId, query);

        if (query == null || query.trim().isEmpty()) {
            return new TeamSearchResponse(true, List.of());
        }

        List<TeamMembership> userMemberships = teamMembershipRepository.findByUserId(userId);
        List<String> userTeamIds = userMemberships.stream()
                .map(m -> m.getTeam().getId())
                .toList();

        List<Team> results = teamRepository.searchTeams(query.trim(), PageRequest.of(0, 20)).getContent();

        List<TeamSearchItem> items = results.stream()
                .map(team -> {
                    String status;
                    if (userTeamIds.contains(team.getId())) {
                        status = "joined";
                    } else if (joinRequestRepository.existsByTeamIdAndUserIdAndStatus(team.getId(), userId, JoinRequestStatus.PENDING)) {
                        status = "pending";
                    } else {
                        status = "not_joined";
                    }
                    return new TeamSearchItem(
                            team.getId(),
                            team.getName(),
                            team.getOrganization() != null ? team.getOrganization().getName() : "Unknown",
                            status
                    );
                })
                .toList();

        return new TeamSearchResponse(true, items);
    }

    @Override
    @Transactional
    public JoinTeamResponse joinTeam(String userId, JoinTeamRequest request) {
        log.info("User {} joining team with invite code: {}", userId, request.inviteCode());

        Team team = teamRepository.findByInviteCode(request.inviteCode())
                .orElseThrow(() -> new NotFoundException("INVITE_CODE_NOT_FOUND", "Invalid invite code."));

        // Check if already a member
        if (teamMembershipRepository.existsByTeamIdAndUserId(team.getId(), userId)) {
            throw new ConflictException("ALREADY_MEMBER", "You are already a member of this team.");
        }

        // Check if already have a pending request
        if (joinRequestRepository.existsByTeamIdAndUserIdAndStatus(team.getId(), userId, JoinRequestStatus.PENDING)) {
            throw new ConflictException("REQUEST_PENDING", "You already have a pending request for this team.");
        }

        // Create join request
        JoinRequest joinRequest = JoinRequest.builder()
                .team(team)
                .userId(userId)
                .inviteCode(request.inviteCode())
                .status(JoinRequestStatus.PENDING)
                .build();

        joinRequestRepository.save(joinRequest);

        String orgId = team.getOrganization() != null ? team.getOrganization().getId().toString() : "";
        String orgName = team.getOrganization() != null ? team.getOrganization().getName() : "Unknown";

        return new JoinTeamResponse(
                true,
                new JoinTeamData(orgId, orgName, team.getId(), "pending"),
                "Join request submitted. Awaiting admin approval."
        );
    }

    @Override
    @Transactional(readOnly = true)
    public Team getTeamById(String teamId) {
        return teamRepository.findById(teamId)
                .orElseThrow(() -> new NotFoundException("TEAM_NOT_FOUND", "Team not found."));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isUserMember(String teamId, String userId) {
        return teamMembershipRepository.existsByTeamIdAndUserId(teamId, userId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CourseTeamResponse> getTeamCourses(String userId, String teamId) {
        log.info("Fetching team courses for userId: {} | teamId: {}", userId, teamId);
        
        // Verify user is a member of the team
        if (!isUserMember(teamId, userId)) {
            throw new com.smartstudy.shared.exception.UnauthorizedException("TEAM_ACCESS_DENIED", "You are not a member of this team.");
        }
        
        try {
            return teamServiceClient.getTeamCourses(teamId, userId);
        } catch (Exception e) {
            log.warn("Failed to fetch team courses from planning-service for team {}: {}", teamId, e.getMessage());
            return List.of();
        }
    }

    // Helper method to generate invite code (for admin use)
    @Transactional
    public String generateInviteCode(String teamId) {
        Team team = getTeamById(teamId);
        String inviteCode = generateRandomCode();
        team.setInviteCode(inviteCode);
        teamRepository.save(team);
        return inviteCode;
    }

    private String generateRandomCode() {
        StringBuilder sb = new StringBuilder(INVITE_CODE_LENGTH);
        Random random = new Random();
        for (int i = 0; i < INVITE_CODE_LENGTH; i++) {
            sb.append(INVITE_CHARS.charAt(random.nextInt(INVITE_CHARS.length())));
        }
        return sb.toString();
    }
}