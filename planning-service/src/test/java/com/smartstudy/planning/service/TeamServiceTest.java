package com.smartstudy.planning.service;

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
import com.smartstudy.shared.exception.ConflictException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TeamServiceTest {

    @Mock
    private TeamRepository teamRepository;

    @Mock
    private TeamMemberRepository teamMemberRepository;

    @Mock
    private CourseRepository courseRepository;

    @Mock
    private EventRepository eventRepository;

    @Mock
    private TaskRepository taskRepository;

    @InjectMocks
    private TeamService teamService;

    private final String userId = "user-1";
    private final String teamId = "team-1";
    private final String orgId = "org-1";
    private final UUID courseId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        reset(teamRepository, teamMemberRepository, courseRepository, eventRepository, taskRepository);
    }

    @Test
    void getUserTeams_noOrgId_returnsAllAcceptedTeams() {
        Team team = Team.builder()
                .id(teamId)
                .name("Team A")
                .organizationId(orgId)
                .organizationName("Org A")
                .build();
        TeamMember member = TeamMember.builder()
                .teamId(teamId)
                .userId(userId)
                .status(TeamMemberStatus.ACCEPTED)
                .build();

        when(teamMemberRepository.findByUserIdAndStatus(userId, TeamMemberStatus.ACCEPTED))
                .thenReturn(List.of(member));
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));

        List<TeamResponse> result = teamService.getUserTeams(userId);

        assertEquals(1, result.size());
        assertEquals("Team A", result.get(0).name());
    }

    @Test
    void getTeamCourses_noOrgId_resolvesFromTeamAndReturnsCourses() {
        Team team = Team.builder()
                .id(teamId)
                .name("Team A")
                .organizationId(orgId)
                .build();
        Course course = Course.builder()
                .id(courseId)
                .userId(userId)
                .name("Course A")
                .teamId(teamId)
                .hidden(false)
                .startDate(Instant.now())
                .build();

        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));
        when(teamMemberRepository.existsByTeamIdAndUserIdAndStatus(teamId, userId, TeamMemberStatus.ACCEPTED))
                .thenReturn(true);
        when(courseRepository.findByTeamIdAndHiddenFalse(teamId)).thenReturn(List.of(course));
        when(taskRepository.countByUserIdAndCourseId(userId, courseId)).thenReturn(10L);
        when(taskRepository.countByUserIdAndCourseIdAndCompletedTrue(userId, courseId)).thenReturn(5L);

        List<TeamCourseResponse> result = teamService.getTeamCourses(teamId, userId);

        assertEquals(1, result.size());
        assertEquals("Course A", result.get(0).name());
    }

    @Test
    void getTeamEvents_noOrgId_resolvesFromTeamAndReturnsEvents() {
        Team team = Team.builder()
                .id(teamId)
                .name("Team A")
                .organizationId(orgId)
                .build();
        Course course = Course.builder()
                .id(courseId)
                .userId(userId)
                .name("Course A")
                .teamId(teamId)
                .build();
        Event event = Event.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .title("Quiz")
                .startDate(Instant.now().plus(1, java.time.temporal.ChronoUnit.DAYS))
                .courseId(courseId)
                .eventType("Quiz")
                .build();

        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));
        when(teamMemberRepository.existsByTeamIdAndUserIdAndStatus(teamId, userId, TeamMemberStatus.ACCEPTED))
                .thenReturn(true);
        when(courseRepository.findByTeamId(teamId)).thenReturn(List.of(course));
        when(eventRepository.findByCourseIdInAndTaskIdIsNull(List.of(courseId))).thenReturn(List.of(event));

        List<TeamEventResponse> result = teamService.getTeamEvents(teamId, userId);

        assertEquals(1, result.size());
        assertEquals("Quiz", result.get(0).eventType());
    }

    @Test
    void discover_noOrgId_returnsPendingAndTrendingAcrossAllOrgs() {
        Team team = Team.builder()
                .id(teamId)
                .name("Team A")
                .organizationId(orgId)
                .organizationName("Org A")
                .imageUrl("https://img")
                .build();
        TeamMember pendingMember = TeamMember.builder()
                .teamId(teamId)
                .userId(userId)
                .status(TeamMemberStatus.PENDING)
                .createdAt(Instant.now())
                .build();

        when(teamMemberRepository.findByUserIdAndStatusIn(eq(userId), any()))
                .thenReturn(List.of(pendingMember));
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));
        when(teamRepository.findAll()).thenReturn(List.of(team));

        DiscoverResponse result = teamService.discover(userId);

        assertEquals(1, result.pendingRequests().size());
        assertEquals("Team A", result.pendingRequests().get(0).name());
    }

    @Test
    void searchTeams_noOrgId_searchesAcrossAllOrgs() {
        Team team = Team.builder()
                .id(teamId)
                .name("Academy Mobile Native")
                .organizationId(orgId)
                .organizationName("Tech Academy")
                .build();

        when(teamRepository.findAll()).thenReturn(List.of(team));
        when(teamMemberRepository.findByUserIdAndStatusIn(eq(userId), any()))
                .thenReturn(List.of());

        List<SearchTeamResponse> result = teamService.searchTeams("Academy", userId);

        assertEquals(1, result.size());
        assertEquals("NOT_A_MEMBER", result.get(0).status());
    }

    @Test
    void joinTeam_noOrgId_createsPendingMembership() {
        Team team = Team.builder()
                .id(teamId)
                .name("Team A")
                .organizationId(orgId)
                .organizationName("Org A")
                .inviteCode("CODE123")
                .build();

        when(teamRepository.findByInviteCode("CODE123")).thenReturn(Optional.of(team));
        when(teamMemberRepository.findByTeamIdAndUserId(teamId, userId)).thenReturn(Optional.empty());

        JoinTeamResponse result = teamService.joinTeam(userId, "CODE123");

        assertEquals("pending", result.status());
        verify(teamMemberRepository).save(any(TeamMember.class));
    }

    @Test
    void joinTeam_noOrgId_alreadyAccepted_throwsConflict() {
        Team team = Team.builder()
                .id(teamId)
                .name("Team A")
                .inviteCode("CODE123")
                .build();
        TeamMember existing = TeamMember.builder()
                .teamId(teamId)
                .userId(userId)
                .status(TeamMemberStatus.ACCEPTED)
                .build();

        when(teamRepository.findByInviteCode("CODE123")).thenReturn(Optional.of(team));
        when(teamMemberRepository.findByTeamIdAndUserId(teamId, userId)).thenReturn(Optional.of(existing));

        assertThrows(ConflictException.class, () -> teamService.joinTeam(userId, "CODE123"));
    }

    @Test
    void toTeamCourseResponse_usesEndDateNotExamDate() {
        Team team = Team.builder()
                .id(teamId)
                .name("Team A")
                .organizationId(orgId)
                .build();
        Course course = Course.builder()
                .id(courseId)
                .userId(userId)
                .name("Course A")
                .teamId(teamId)
                .startDate(Instant.parse("2026-07-30T13:11:20Z"))
                .endDate(Instant.parse("2026-10-30T14:11:20Z"))
                .examDate(Instant.parse("2026-12-01T00:00:00Z"))
                .build();

        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));
        when(teamMemberRepository.existsByTeamIdAndUserIdAndStatus(teamId, userId, TeamMemberStatus.ACCEPTED))
                .thenReturn(true);
        when(courseRepository.findByTeamIdAndHiddenFalse(teamId)).thenReturn(List.of(course));
        when(taskRepository.countByUserIdAndCourseId(userId, courseId)).thenReturn(10L);
        when(taskRepository.countByUserIdAndCourseIdAndCompletedTrue(userId, courseId)).thenReturn(5L);

        List<TeamCourseResponse> result = teamService.getTeamCourses(teamId, userId);
        TeamCourseResponse response = result.get(0);

        assertEquals(Instant.parse("2026-10-30T14:11:20Z"), response.endDate());
    }
}
