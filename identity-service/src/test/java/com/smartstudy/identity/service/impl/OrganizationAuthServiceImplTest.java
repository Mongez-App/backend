package com.smartstudy.identity.service.impl;

import com.smartstudy.identity.dto.request.OrganizationUpdateRequest;
import com.smartstudy.identity.dto.response.OrganizationDataResponse;
import com.smartstudy.identity.model.Organization;
import com.smartstudy.identity.repository.OrganizationRepository;
import com.smartstudy.identity.repository.UserRepository;
import com.smartstudy.shared.exception.BadRequestException;
import com.smartstudy.shared.exception.ConflictException;
import com.smartstudy.shared.exception.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrganizationAuthServiceImplTest {

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private OrganizationAuthServiceImpl organizationAuthService;

    private final String uid = "firebase-uid-123";
    private final String email = "org@example.com";
    private final String name = "Test Organization";

    @BeforeEach
    void setUp() {
        lenient().when(organizationRepository.existsById(uid)).thenReturn(false);
        lenient().when(userRepository.existsById(uid)).thenReturn(false);
    }

    @Test
    void testRegister_NewOrganization_Success() {
        Organization savedOrg = Organization.builder()
                .id(uid)
                .email(email)
                .name(name)
                .noOfStudents(0)
                .noOfCourses(0)
                .noOfTeams(0)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(organizationRepository.save(any(Organization.class))).thenReturn(savedOrg);

        OrganizationDataResponse response = organizationAuthService.register(uid, email, name);

        assertNotNull(response);
        assertEquals(uid, response.uid());
        assertEquals(email, response.email());
        assertEquals(name, response.name());
        assertEquals(0, response.no_of_students());
        assertEquals(0, response.no_of_courses());
        assertEquals(0, response.no_of_teams());

        verify(organizationRepository).save(any(Organization.class));
    }

    @Test
    void testRegister_OrganizationAlreadyExists_ThrowsConflict() {
        when(organizationRepository.existsById(uid)).thenReturn(true);

        ConflictException exception = assertThrows(ConflictException.class,
                () -> organizationAuthService.register(uid, email, name));
        assertEquals("ORGANIZATION_ALREADY_EXISTS", exception.getErrorCode());
        verify(organizationRepository, never()).save(any());
    }

    @Test
    void testRegister_UserAlreadyExistsWithSameUid_ThrowsConflict() {
        when(organizationRepository.existsById(uid)).thenReturn(false);
        when(userRepository.existsById(uid)).thenReturn(true);

        ConflictException exception = assertThrows(ConflictException.class,
                () -> organizationAuthService.register(uid, email, name));
        assertEquals("UID_ALREADY_IN_USE", exception.getErrorCode());
        verify(organizationRepository, never()).save(any());
    }

    @Test
    void testRegister_NullEmail_ThrowsBadRequest() {
        when(organizationRepository.existsById(uid)).thenReturn(false);
        when(userRepository.existsById(uid)).thenReturn(false);

        BadRequestException exception = assertThrows(BadRequestException.class,
                () -> organizationAuthService.register(uid, null, name));
        assertEquals("EMAIL_REQUIRED", exception.getErrorCode());
        verify(organizationRepository, never()).save(any());
    }

    @Test
    void testRegister_BlankEmail_ThrowsBadRequest() {
        when(organizationRepository.existsById(uid)).thenReturn(false);
        when(userRepository.existsById(uid)).thenReturn(false);

        BadRequestException exception = assertThrows(BadRequestException.class,
                () -> organizationAuthService.register(uid, "  ", name));
        assertEquals("EMAIL_REQUIRED", exception.getErrorCode());
        verify(organizationRepository, never()).save(any());
    }

    @Test
    void testLogin_OrganizationFound_ReturnsResponse() {
        Organization org = Organization.builder()
                .id(uid)
                .email(email)
                .name(name)
                .noOfStudents(10)
                .noOfCourses(5)
                .noOfTeams(2)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(organizationRepository.findById(uid)).thenReturn(Optional.of(org));

        OrganizationDataResponse response = organizationAuthService.login(uid);

        assertNotNull(response);
        assertEquals(uid, response.uid());
        assertEquals(name, response.name());
        assertEquals(10, response.no_of_students());
        assertEquals(5, response.no_of_courses());
        assertEquals(2, response.no_of_teams());
    }

    @Test
    void testLogin_OrganizationNotFound_ThrowsNotFound() {
        when(organizationRepository.findById(uid)).thenReturn(Optional.empty());

        NotFoundException exception = assertThrows(NotFoundException.class,
                () -> organizationAuthService.login(uid));
        assertEquals("ORGANIZATION_NOT_FOUND", exception.getErrorCode());
    }

    @Test
    void testUpdate_OrganizationFound_UpdatesName() {
        Organization existing = Organization.builder()
                .id(uid)
                .email(email)
                .name("Old Name")
                .noOfStudents(0)
                .noOfCourses(0)
                .noOfTeams(0)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(organizationRepository.findById(uid)).thenReturn(Optional.of(existing));
        when(organizationRepository.save(any(Organization.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrganizationUpdateRequest request = new OrganizationUpdateRequest("New Name", null, null);
        OrganizationDataResponse response = organizationAuthService.update(uid, request);

        assertNotNull(response);
        assertEquals("New Name", response.name());
        verify(organizationRepository).save(any(Organization.class));
    }

    @Test
    void testUpdate_OrganizationFound_UpdatesDescriptionAndAvatar() {
        Organization existing = Organization.builder()
                .id(uid)
                .email(email)
                .name(name)
                .noOfStudents(0)
                .noOfCourses(0)
                .noOfTeams(0)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(organizationRepository.findById(uid)).thenReturn(Optional.of(existing));
        when(organizationRepository.save(any(Organization.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrganizationUpdateRequest request = new OrganizationUpdateRequest(
                null, "New Description", "https://example.com/avatar.png");
        OrganizationDataResponse response = organizationAuthService.update(uid, request);

        assertNotNull(response);
        assertEquals("New Description", response.description());
        assertEquals("https://example.com/avatar.png", response.avatar());
    }

    @Test
    void testUpdate_OrganizationNotFound_ThrowsNotFound() {
        when(organizationRepository.findById(uid)).thenReturn(Optional.empty());

        OrganizationUpdateRequest request = new OrganizationUpdateRequest("New Name", null, null);

        NotFoundException exception = assertThrows(NotFoundException.class,
                () -> organizationAuthService.update(uid, request));
        assertEquals("ORGANIZATION_NOT_FOUND", exception.getErrorCode());
        verify(organizationRepository, never()).save(any());
    }

    @Test
    void testUpdate_NoFieldsProvided_ThrowsBadRequest() {
        Organization existing = Organization.builder()
                .id(uid)
                .email(email)
                .name(name)
                .noOfStudents(0)
                .noOfCourses(0)
                .noOfTeams(0)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(organizationRepository.findById(uid)).thenReturn(Optional.of(existing));

        OrganizationUpdateRequest request = new OrganizationUpdateRequest(null, null, null);

        BadRequestException exception = assertThrows(BadRequestException.class,
                () -> organizationAuthService.update(uid, request));
        assertEquals("INVALID_REQUEST", exception.getErrorCode());
        verify(organizationRepository, never()).save(any());
    }

    @Test
    void testUpdate_EmptyFieldsIgnored() {
        Organization existing = Organization.builder()
                .id(uid)
                .email(email)
                .name(name)
                .noOfStudents(0)
                .noOfCourses(0)
                .noOfTeams(0)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(organizationRepository.findById(uid)).thenReturn(Optional.of(existing));

        OrganizationUpdateRequest request = new OrganizationUpdateRequest("", "  ", null);

        BadRequestException exception = assertThrows(BadRequestException.class,
                () -> organizationAuthService.update(uid, request));
        assertEquals("INVALID_REQUEST", exception.getErrorCode());
        verify(organizationRepository, never()).save(any());
    }
}
