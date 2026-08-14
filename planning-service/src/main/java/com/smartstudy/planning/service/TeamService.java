package com.smartstudy.planning.service;

import com.smartstudy.planning.dto.request.CreateTeamRequest;
import com.smartstudy.planning.dto.request.JoinTeamRequest;
import com.smartstudy.planning.dto.response.*;
import com.smartstudy.planning.enums.TeamMemberStatus;
import com.smartstudy.planning.model.Course;
import com.smartstudy.planning.model.Event;
import com.smartstudy.planning.model.Task;
import com.smartstudy.planning.model.Team;
import com.smartstudy.planning.model.TeamMember;
import com.smartstudy.planning.repository.CourseRepository;
import com.smartstudy.planning.repository.EventRepository;
import com.smartstudy.planning.repository.TaskRepository;
import com.smartstudy.planning.repository.TeamMemberRepository;
import com.smartstudy.planning.repository.TeamRepository;
import com.smartstudy.shared.exception.BadRequestException;
import com.smartstudy.shared.exception.ConflictException;
import com.smartstudy.shared.exception.NotFoundException;
import com.smartstudy.shared.logging.LoggerFactory;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class TeamService {

    private static final Logger log = LoggerFactory.getLogger(TeamService.class);

    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final CourseRepository courseRepository;
    private final EventRepository eventRepository;
    private final TaskRepository taskRepository;

    public List<TeamResponse> getUserTeams(String userId, String orgId) {
        log.info("Fetching teams for userId: {} in org: {}", userId, orgId);
        List<TeamMember> memberships = teamMemberRepository.findByUserIdAndStatus(userId, TeamMemberStatus.ACCEPTED);
        return memberships.stream()
                .map(TeamMember::getTeamId)
                .map(teamId -> teamRepository.findById(teamId).orElse(null))
                .filter(Objects::nonNull)
                .filter(team -> orgId.equals(team.getOrganizationId()))
                .map(team -> toTeamResponse(team, userId))
                .toList();
    }

    public List<TeamCourseResponse> getTeamCourses(String teamId, String userId, String orgId) {
        log.info("Fetching courses for team: {} | userId: {} | org: {}", teamId, userId, orgId);
        validateTeamInOrg(teamId, orgId);
        ensureMember(teamId, userId);
        List<Course> courses = courseRepository.findByTeamIdAndHiddenFalse(teamId);
        return courses.stream()
                .map(course -> toTeamCourseResponse(course, userId))
                .toList();
    }

    public List<TeamEventResponse> getTeamEvents(String teamId, String userId, String orgId) {
        log.info("Fetching events for team: {} | userId: {} | org: {}", teamId, userId, orgId);
        validateTeamInOrg(teamId, orgId);
        ensureMember(teamId, userId);
        List<Course> courses = courseRepository.findByTeamId(teamId);
        List<UUID> courseIds = courses.stream().map(Course::getId).toList();
        if (courseIds.isEmpty()) {
            return List.of();
        }
        List<Event> events = eventRepository.findByCourseIdInAndTaskIdIsNull(courseIds);
        Map<UUID, String> courseNameMap = courses.stream().collect(Collectors.toMap(Course::getId, Course::getName));
        return events.stream()
                .map(event -> {
                    String courseName = courseNameMap.get(event.getCourseId());
                    return new TeamEventResponse(
                            event.getId(),
                            courseName,
                            event.getEventType(),
                            dueText(event.getStartDate()),
                            event.getStartDate()
                    );
                })
                .toList();
    }

    public DiscoverResponse discover(String userId, String orgId) {
        log.info("Discovering for userId: {} in org: {}", userId, orgId);
        List<TeamMember> allMemberships = teamMemberRepository.findByUserIdAndStatusIn(
                userId, List.of(TeamMemberStatus.PENDING, TeamMemberStatus.ACCEPTED));
        Set<String> userTeamIds = allMemberships.stream()
                .map(TeamMember::getTeamId)
                .collect(Collectors.toSet());

        List<PendingRequestResponse> pendingRequests = allMemberships.stream()
                .filter(m -> m.getStatus() == TeamMemberStatus.PENDING)
                .map(TeamMember::getTeamId)
                .map(teamId -> teamRepository.findById(teamId).orElse(null))
                .filter(Objects::nonNull)
                .filter(team -> orgId.equals(team.getOrganizationId()))
                .map(team -> {
                    TeamMember member = allMemberships.stream()
                            .filter(m -> team.getId().equals(m.getTeamId()))
                            .findFirst()
                            .orElse(null);
                    return new PendingRequestResponse(
                            team.getId(),
                            team.getName(),
                            team.getImageUrl(),
                            team.getOrganizationName(),
                            member != null ? member.getCreatedAt() : Instant.now(),
                            "pending"
                    );
                })
                .toList();

        List<TrendingTeamResponse> trendingTeams = teamRepository.findAll().stream()
                .filter(team -> orgId.equals(team.getOrganizationId()))
                .filter(team -> !userTeamIds.contains(team.getId()))
                .limit(10)
                .map(team -> new TrendingTeamResponse(
                        team.getId(),
                        team.getName(),
                        team.getImageUrl(),
                        team.getOrganizationName(),
                        "NOT_A_MEMBER"
                ))
                .toList();

        return new DiscoverResponse(pendingRequests, trendingTeams);
    }

    public List<SearchTeamResponse> searchTeams(String query, String userId, String orgId) {
        log.info("Searching teams for query: {} | userId: {} | org: {}", query, userId, orgId);
        String needle = query.toLowerCase();
        List<Team> teams = teamRepository.findAll().stream()
                .filter(team -> orgId.equals(team.getOrganizationId()))
                .filter(team -> team.getName().toLowerCase().contains(needle)
                        || (team.getOrganizationName() != null
                                && team.getOrganizationName().toLowerCase().contains(needle)))
                .toList();

        List<TeamMember> allMemberships = teamMemberRepository.findByUserIdAndStatusIn(
                userId, List.of(TeamMemberStatus.PENDING, TeamMemberStatus.ACCEPTED));
        Map<String, TeamMember> membershipMap = allMemberships.stream()
                .collect(Collectors.toMap(TeamMember::getTeamId, m -> m));

        return teams.stream()
                .map(team -> {
                    TeamMember member = membershipMap.get(team.getId());
                    String status = switch (member != null ? member.getStatus() : null) {
                        case ACCEPTED -> "ALREADY_A_MEMBER";
                        case PENDING -> "PENDING";
                        default -> "NOT_A_MEMBER";
                    };
                    return new SearchTeamResponse(
                            team.getId(),
                            team.getName(),
                            team.getOrganizationName(),
                            status,
                            team.getImageUrl()
                    );
                })
                .toList();
    }

    public JoinTeamResponse joinTeam(String userId, String orgId, String inviteCode) {
        log.info("Joining team with invite code: {} | userId: {} | org: {}", inviteCode, userId, orgId);
        Team team = teamRepository.findByInviteCode(inviteCode)
                .orElseThrow(() -> new BadRequestException("INVALID_INVITE_CODE", "Invalid invite code."));
        if (!orgId.equals(team.getOrganizationId())) {
            throw new BadRequestException("ORG_MISMATCH", "Team does not belong to the specified organization.");
        }
        Optional<TeamMember> existing = teamMemberRepository.findByTeamIdAndUserId(team.getId(), userId);
        if (existing.isPresent()) {
            TeamMemberStatus status = existing.get().getStatus();
            if (status == TeamMemberStatus.ACCEPTED) {
                throw new ConflictException("ALREADY_A_MEMBER", "You are already a member of this team.");
            }
            if (status == TeamMemberStatus.PENDING) {
                throw new ConflictException("PENDING_REQUEST_EXISTS", "Your join request is pending.");
            }
        }
        TeamMember member = TeamMember.builder()
                .teamId(team.getId())
                .userId(userId)
                .status(TeamMemberStatus.PENDING)
                .build();
        teamMemberRepository.save(member);
        return new JoinTeamResponse(
                team.getOrganizationId(),
                team.getOrganizationName(),
                team.getId(),
                team.getName(),
                "pending",
                "Join request submitted. Awaiting admin approval."
        );
    }

    public TeamResponse createTeam(String userId, String orgId, CreateTeamRequest request) {
        log.info("Creating team in org: {} | userId: {} | name: {}", orgId, userId, request.name());
        String inviteCode = request.inviteCode();
        if (inviteCode == null || inviteCode.isBlank()) {
            inviteCode = generateUniqueInviteCode();
        } else {
            if (teamRepository.findByInviteCode(inviteCode).isPresent()) {
                throw new ConflictException("INVITE_CODE_EXISTS", "Invite code already in use.");
            }
        }
        Team team = Team.builder()
                .id(UUID.randomUUID().toString())
                .name(request.name())
                .organizationId(orgId)
                .organizationName(null)
                .imageUrl(request.imageUrl())
                .inviteCode(inviteCode)
                .build();
        teamRepository.save(team);

        TeamMember creator = TeamMember.builder()
                .teamId(team.getId())
                .userId(userId)
                .status(TeamMemberStatus.ACCEPTED)
                .build();
        teamMemberRepository.save(creator);
        return toTeamResponse(team, userId);
    }

    public void validateTeamInOrg(String teamId, String orgId) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new NotFoundException("TEAM_NOT_FOUND", "Team not found."));
        if (!orgId.equals(team.getOrganizationId())) {
            throw new NotFoundException("TEAM_NOT_FOUND", "Team not found in the specified organization.");
        }
    }

    public void ensureMember(String teamId, String userId) {
        boolean isMember = teamMemberRepository.existsByTeamIdAndUserIdAndStatus(
                teamId, userId, TeamMemberStatus.ACCEPTED);
        if (!isMember) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "NOT_A_TEAM_MEMBER");
        }
    }

    private String generateUniqueInviteCode() {
        String code;
        do {
            code = UUID.randomUUID().toString().substring(0, 8);
        } while (teamRepository.findByInviteCode(code).isPresent());
        return code;
    }

    private TeamResponse toTeamResponse(Team team, String userId) {
        List<Course> courses = courseRepository.findByTeamId(team.getId());
        double completionPercentage = 0.0;
        if (!courses.isEmpty()) {
            double totalPercentage = courses.stream()
                    .mapToDouble(course -> completionPercentageForCourse(course.getId(), userId))
                    .average()
                    .orElse(0.0);
            completionPercentage = Math.round(totalPercentage * 100.0) / 100.0;
        }
        List<UUID> courseIds = courses.stream().map(Course::getId).toList();
        List<TeamEventSummary> eventSummaries = List.of();
        if (!courseIds.isEmpty()) {
            Instant now = Instant.now();
            Instant limit = now.plus(7, java.time.temporal.ChronoUnit.DAYS);
            List<Event> events = eventRepository.findByCourseIdInAndTaskIdIsNull(courseIds).stream()
                    .filter(e -> !e.getStartDate().isAfter(limit))
                    .toList();
            Set<String> eventTypes = events.stream()
                    .map(Event::getEventType)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            eventSummaries = eventTypes.stream()
                    .map(TeamEventSummary::new)
                    .toList();
        }
        return new TeamResponse(
                team.getId(),
                team.getName(),
                team.getOrganizationName(),
                team.getImageUrl(),
                completionPercentage,
                eventSummaries
        );
    }

    private TeamCourseResponse toTeamCourseResponse(Course course, String userId) {
        return new TeamCourseResponse(
                course.getId(),
                course.getTeamId(),
                course.getUserId(),
                course.getName(),
                course.getCourseCode(),
                course.getStartDate(),
                course.getExamDate(),
                course.getImageUrl(),
                completionPercentageForCourse(course.getId(), userId)
        );
    }

    private double completionPercentageForCourse(UUID courseId, String userId) {
        long total = taskRepository.countByUserIdAndCourseId(userId, courseId);
        if (total == 0) {
            return 0.0;
        }
        long completed = taskRepository.countByUserIdAndCourseIdAndCompletedTrue(userId, courseId);
        return Math.round((completed * 10000.0) / total) / 100.0;
    }

    private String dueText(Instant dueDate) {
        long days = Duration.between(Instant.now(), dueDate).toDays();
        if (days <= 0) {
            return "Today";
        }
        if (days == 1) {
            return "Tomorrow";
        }
        return days + " Days left";
    }
}
