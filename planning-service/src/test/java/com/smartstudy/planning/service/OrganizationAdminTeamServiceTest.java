package com.smartstudy.planning.service;

import com.smartstudy.planning.client.IdentityServiceClient;
import com.smartstudy.planning.config.StorageProperties;
import com.smartstudy.planning.dto.request.OrgCreateTeamRequest;
import com.smartstudy.planning.exception.OrgApiException;
import com.smartstudy.planning.exception.OrganizationLookupUnavailableException;
import com.smartstudy.planning.model.Team;
import com.smartstudy.planning.repository.CourseRepository;
import com.smartstudy.planning.repository.EventRepository;
import com.smartstudy.planning.repository.MaterialRepository;
import com.smartstudy.planning.repository.TaskRepository;
import com.smartstudy.planning.repository.TeamMemberRepository;
import com.smartstudy.planning.repository.TeamRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Invite codes are handed to students off-platform, so an admin has to be able
 * to choose the code rather than discover a generated one.
 */
@ExtendWith(MockitoExtension.class)
class OrganizationAdminTeamServiceTest {

    @Mock private TeamRepository teamRepository;
    @Mock private TeamMemberRepository teamMemberRepository;
    @Mock private CourseRepository courseRepository;
    @Mock private MaterialRepository materialRepository;
    @Mock private EventRepository eventRepository;
    @Mock private TaskRepository taskRepository;
    @Mock private FileStorageService fileStorageService;
    @Mock private StorageProperties storageProperties;
    @Mock private IdentityServiceClient identityServiceClient;
    @Mock private OrganizationNameResolver organizationNameResolver;

    @InjectMocks
    private OrganizationAdminService service;

    private static final String ORG_ID = "org-1";

    private void stubSave() {
        when(teamRepository.save(any(Team.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private Team captureSavedTeam() {
        ArgumentCaptor<Team> captor = ArgumentCaptor.forClass(Team.class);
        verify(teamRepository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void createTeam_usesTheInviteCodeTheAdminChose() {
        stubSave();
        when(teamRepository.findByInviteCode("12345678")).thenReturn(Optional.empty());

        service.createTeam(ORG_ID, new OrgCreateTeamRequest("Team A", null, "12345678"));

        assertEquals("12345678", captureSavedTeam().getInviteCode());
    }

    @Test
    void createTeam_trimsTheChosenCode() {
        stubSave();
        lenient().when(teamRepository.findByInviteCode(anyString())).thenReturn(Optional.empty());

        service.createTeam(ORG_ID, new OrgCreateTeamRequest("Team A", null, "  12345678  "));

        assertEquals("12345678", captureSavedTeam().getInviteCode());
    }

    @Test
    void createTeam_generatesOneWhenNoneIsGiven() {
        stubSave();
        lenient().when(teamRepository.findByInviteCode(anyString())).thenReturn(Optional.empty());

        service.createTeam(ORG_ID, new OrgCreateTeamRequest("Team A", null, null));

        String generated = captureSavedTeam().getInviteCode();
        assertNotNull(generated);
        assertEquals(8, generated.length());
    }

    @Test
    void createTeam_blankCodeIsTreatedAsAbsent() {
        stubSave();
        lenient().when(teamRepository.findByInviteCode(anyString())).thenReturn(Optional.empty());

        service.createTeam(ORG_ID, new OrgCreateTeamRequest("Team A", null, "   "));

        assertNotNull(captureSavedTeam().getInviteCode());
    }

    @Test
    void createTeam_codeAlreadyTakenIsConflict() {
        when(teamRepository.findByInviteCode("12345678"))
                .thenReturn(Optional.of(Team.builder().id("other").build()));

        OrgApiException ex = assertThrows(OrgApiException.class, () ->
                service.createTeam(ORG_ID, new OrgCreateTeamRequest("Team A", null, "12345678")));

        assertEquals(409, ex.getStatus().value());
        verify(teamRepository, never()).save(any());
    }

    @Test
    void createTeam_overlongCodeIsRejectedBeforeItReachesTheColumn() {
        OrgApiException ex = assertThrows(OrgApiException.class, () ->
                service.createTeam(ORG_ID, new OrgCreateTeamRequest("Team A", null, "x".repeat(65))));

        assertEquals(400, ex.getStatus().value());
        verify(teamRepository, never()).save(any());
    }

    @Test
    void createTeam_codeWithSpacesIsRejected() {
        OrgApiException ex = assertThrows(OrgApiException.class, () ->
                service.createTeam(ORG_ID, new OrgCreateTeamRequest("Team A", null, "12 345678")));

        assertEquals(400, ex.getStatus().value());
        assertTrue(ex.getMessage().contains("spaces"));
    }

    // X-User-Id carries the organization's uid, not its name, so the name has to
    // be resolved from identity-service and copied onto the team. Teams saved
    // without it drop out of organization-name search and pass the null on to
    // every course created under them.

    @Test
    void createTeam_stampsTheResolvedOrganizationName() {
        stubSave();
        lenient().when(teamRepository.findByInviteCode(anyString())).thenReturn(Optional.empty());
        when(organizationNameResolver.resolve(ORG_ID)).thenReturn("Acme University");

        service.createTeam(ORG_ID, new OrgCreateTeamRequest("Team A", null, null));

        assertEquals("Acme University", captureSavedTeam().getOrganizationName());
    }

    @Test
    void createTeam_unresolvableOrganizationStillCreatesTheTeam() {
        stubSave();
        lenient().when(teamRepository.findByInviteCode(anyString())).thenReturn(Optional.empty());
        when(organizationNameResolver.resolve(ORG_ID)).thenReturn(null);

        service.createTeam(ORG_ID, new OrgCreateTeamRequest("Team A", null, null));

        assertNull(captureSavedTeam().getOrganizationName());
    }

    @Test
    void createTeam_failsRatherThanSaveANullNameWhenIdentityServiceIsDown() {
        lenient().when(teamRepository.findByInviteCode(anyString())).thenReturn(Optional.empty());
        when(organizationNameResolver.resolve(ORG_ID))
                .thenThrow(new OrganizationLookupUnavailableException("identity-service is unreachable.", null));

        assertThrows(OrganizationLookupUnavailableException.class, () ->
                service.createTeam(ORG_ID, new OrgCreateTeamRequest("Team A", null, null)));

        verify(teamRepository, never()).save(any());
    }
}
