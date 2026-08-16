package com.smartstudy.planning.service;

import com.smartstudy.planning.client.IdentityServiceClient;
import com.smartstudy.planning.config.StorageProperties;
import com.smartstudy.planning.dto.response.OrgMemberActionResponse;
import com.smartstudy.planning.dto.response.OrgMemberListResponse;
import com.smartstudy.planning.dto.response.UserSummaryData;
import com.smartstudy.planning.enums.TeamMemberStatus;
import com.smartstudy.planning.exception.OrgApiException;
import com.smartstudy.planning.model.Team;
import com.smartstudy.planning.model.TeamMember;
import com.smartstudy.planning.repository.CourseRepository;
import com.smartstudy.planning.repository.EventRepository;
import com.smartstudy.planning.repository.MaterialRepository;
import com.smartstudy.planning.repository.TaskRepository;
import com.smartstudy.planning.repository.TeamMemberRepository;
import com.smartstudy.planning.repository.TeamRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers the member half of the org contract: getMembers / acceptMember /
 * declineMember.
 */
@ExtendWith(MockitoExtension.class)
class OrganizationAdminMemberServiceTest {

    @Mock private TeamRepository teamRepository;
    @Mock private TeamMemberRepository teamMemberRepository;
    @Mock private CourseRepository courseRepository;
    @Mock private MaterialRepository materialRepository;
    @Mock private EventRepository eventRepository;
    @Mock private TaskRepository taskRepository;
    @Mock private FileStorageService fileStorageService;
    @Mock private StorageProperties storageProperties;
    @Mock private IdentityServiceClient identityServiceClient;

    @InjectMocks
    private OrganizationAdminService service;

    private static final String ORG_ID = "org-1";
    private static final String TEAM_ID = "team-1";

    private Team team() {
        return Team.builder().id(TEAM_ID).name("Team A").organizationId(ORG_ID).build();
    }

    private TeamMember member(UUID id, String userId, TeamMemberStatus status) {
        TeamMember member = TeamMember.builder()
                .id(id)
                .teamId(TEAM_ID)
                .userId(userId)
                .status(status)
                .build();
        member.setCreatedAt(Instant.parse("2026-08-12T14:00:00Z"));
        member.setUpdatedAt(Instant.parse("2026-08-12T14:00:00Z"));
        return member;
    }

    @Test
    void getMembers_splitsPendingFromActiveAndResolvesNames() {
        UUID pendingId = UUID.randomUUID();
        UUID activeId = UUID.randomUUID();
        TeamMember pending = member(pendingId, "uid-pending", TeamMemberStatus.PENDING);
        TeamMember active = member(activeId, "uid-active", TeamMemberStatus.ACCEPTED);
        active.setJoinedAt(Instant.parse("2026-07-01T10:00:00Z"));

        when(teamRepository.findById(TEAM_ID)).thenReturn(Optional.of(team()));
        when(teamMemberRepository.findByTeamIdAndStatus(TEAM_ID, TeamMemberStatus.PENDING))
                .thenReturn(List.of(pending));
        when(teamMemberRepository.findByTeamIdAndStatus(TEAM_ID, TeamMemberStatus.ACCEPTED))
                .thenReturn(List.of(active));
        when(identityServiceClient.lookupUsers(eq(ORG_ID), anyList())).thenReturn(List.of(
                new UserSummaryData("uid-pending", "Nour Adel", "nour@example.com"),
                new UserSummaryData("uid-active", "Mona Nabil", "mona@example.com")));

        OrgMemberListResponse response = service.getMembers(ORG_ID, TEAM_ID);

        assertEquals(1, response.pendingTotal());
        assertEquals(1, response.teamTotal());

        var pendingRow = response.pendingMembers().get(0);
        assertEquals(pendingId.toString(), pendingRow.id());
        assertEquals("Nour Adel", pendingRow.name());
        assertEquals("NA", pendingRow.avatarInitials());
        assertEquals("pending", pendingRow.status());
        assertEquals(Instant.parse("2026-08-12T14:00:00Z"), pendingRow.requestedAt());
        assertNull(pendingRow.joinedAt());
        assertNotNull(pendingRow.avatarColor());

        var activeRow = response.teamMembers().get(0);
        assertEquals("active", activeRow.status());
        assertEquals(Instant.parse("2026-07-01T10:00:00Z"), activeRow.joinedAt());
        assertNull(activeRow.requestedAt());
    }

    @Test
    void getMembers_identityUnavailable_stillReturnsRows() {
        TeamMember pending = member(UUID.randomUUID(), "uid-pending", TeamMemberStatus.PENDING);

        when(teamRepository.findById(TEAM_ID)).thenReturn(Optional.of(team()));
        when(teamMemberRepository.findByTeamIdAndStatus(TEAM_ID, TeamMemberStatus.PENDING))
                .thenReturn(List.of(pending));
        when(teamMemberRepository.findByTeamIdAndStatus(TEAM_ID, TeamMemberStatus.ACCEPTED))
                .thenReturn(List.of());
        when(identityServiceClient.lookupUsers(eq(ORG_ID), anyList())).thenReturn(List.of());

        OrgMemberListResponse response = service.getMembers(ORG_ID, TEAM_ID);

        assertEquals(1, response.pendingTotal());
        assertEquals("Member uid-pe", response.pendingMembers().get(0).name());
    }

    @Test
    void getMembers_teamOfAnotherOrg_isForbidden() {
        Team foreign = Team.builder().id(TEAM_ID).organizationId("other-org").build();
        when(teamRepository.findById(TEAM_ID)).thenReturn(Optional.of(foreign));

        OrgApiException ex = assertThrows(OrgApiException.class, () -> service.getMembers(ORG_ID, TEAM_ID));
        assertEquals(403, ex.getStatus().value());
    }

    @Test
    void acceptMember_movesPendingToActiveAndStampsJoinedAt() {
        UUID memberId = UUID.randomUUID();
        TeamMember pending = member(memberId, "uid-1", TeamMemberStatus.PENDING);
        when(teamMemberRepository.findById(memberId)).thenReturn(Optional.of(pending));
        when(teamRepository.findById(TEAM_ID)).thenReturn(Optional.of(team()));

        OrgMemberActionResponse response = service.acceptMember(ORG_ID, memberId.toString());

        assertEquals(memberId.toString(), response.id());
        assertEquals(TEAM_ID, response.teamId());
        assertEquals("active", response.status());
        assertNotNull(response.joinedAt());
        assertEquals(TeamMemberStatus.ACCEPTED, pending.getStatus());
        verify(teamMemberRepository).save(pending);
    }

    @Test
    void acceptMember_alreadyActive_isConflict() {
        UUID memberId = UUID.randomUUID();
        when(teamMemberRepository.findById(memberId))
                .thenReturn(Optional.of(member(memberId, "uid-1", TeamMemberStatus.ACCEPTED)));
        when(teamRepository.findById(TEAM_ID)).thenReturn(Optional.of(team()));

        OrgApiException ex = assertThrows(OrgApiException.class,
                () -> service.acceptMember(ORG_ID, memberId.toString()));
        assertEquals(409, ex.getStatus().value());
        verify(teamMemberRepository, never()).save(any());
    }

    @Test
    void acceptMember_unknownId_isNotFound() {
        UUID memberId = UUID.randomUUID();
        when(teamMemberRepository.findById(memberId)).thenReturn(Optional.empty());

        OrgApiException ex = assertThrows(OrgApiException.class,
                () -> service.acceptMember(ORG_ID, memberId.toString()));
        assertEquals(404, ex.getStatus().value());
    }

    @Test
    void acceptMember_malformedId_isNotFoundRatherThanServerError() {
        OrgApiException ex = assertThrows(OrgApiException.class,
                () -> service.acceptMember(ORG_ID, "member_1a2b3c"));
        assertEquals(404, ex.getStatus().value());
        verify(teamMemberRepository, never()).findById(any());
    }

    @Test
    void acceptMember_memberOfAnotherOrgsTeam_isForbidden() {
        UUID memberId = UUID.randomUUID();
        when(teamMemberRepository.findById(memberId))
                .thenReturn(Optional.of(member(memberId, "uid-1", TeamMemberStatus.PENDING)));
        when(teamRepository.findById(TEAM_ID))
                .thenReturn(Optional.of(Team.builder().id(TEAM_ID).organizationId("other-org").build()));

        OrgApiException ex = assertThrows(OrgApiException.class,
                () -> service.acceptMember(ORG_ID, memberId.toString()));
        assertEquals(403, ex.getStatus().value());
    }

    @Test
    void declineMember_rejectsWithoutJoinedAt() {
        UUID memberId = UUID.randomUUID();
        TeamMember pending = member(memberId, "uid-1", TeamMemberStatus.PENDING);
        when(teamMemberRepository.findById(memberId)).thenReturn(Optional.of(pending));
        when(teamRepository.findById(TEAM_ID)).thenReturn(Optional.of(team()));

        OrgMemberActionResponse response = service.declineMember(ORG_ID, memberId.toString());

        assertEquals("declined", response.status());
        assertNull(response.joinedAt());
        assertEquals(TeamMemberStatus.REJECTED, pending.getStatus());
        assertNull(pending.getJoinedAt());
    }

    @Test
    void declineMember_alreadyDeclined_isNotFound() {
        UUID memberId = UUID.randomUUID();
        when(teamMemberRepository.findById(memberId))
                .thenReturn(Optional.of(member(memberId, "uid-1", TeamMemberStatus.REJECTED)));
        when(teamRepository.findById(TEAM_ID)).thenReturn(Optional.of(team()));

        OrgApiException ex = assertThrows(OrgApiException.class,
                () -> service.declineMember(ORG_ID, memberId.toString()));
        assertEquals(404, ex.getStatus().value());
    }
}
