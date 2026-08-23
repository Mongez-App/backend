package com.smartstudy.identity.service.impl;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.smartstudy.identity.dto.request.OrganizationUpdateRequest;
import com.smartstudy.identity.dto.response.OrganizationDataResponse;
import com.smartstudy.identity.model.Organization;
import com.smartstudy.identity.repository.OrganizationRepository;
import com.smartstudy.identity.repository.UserRepository;
import com.smartstudy.identity.service.OrganizationAuthService;
import com.smartstudy.shared.exception.BadRequestException;
import com.smartstudy.shared.exception.ConflictException;
import com.smartstudy.shared.exception.NotFoundException;
import com.smartstudy.shared.exception.UnauthorizedException;
import com.smartstudy.shared.logging.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class OrganizationAuthServiceImpl implements OrganizationAuthService {

    private static final org.slf4j.Logger log = LoggerFactory.getLogger(OrganizationAuthServiceImpl.class);
    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;

    public OrganizationAuthServiceImpl(OrganizationRepository organizationRepository, UserRepository userRepository) {
        this.organizationRepository = organizationRepository;
        this.userRepository = userRepository;
    }

    @Override
    @Transactional
    public OrganizationDataResponse register(String uid, String email, String name) {
        if (organizationRepository.existsById(uid)) {
            throw new ConflictException("ORGANIZATION_ALREADY_EXISTS", "Organization already registered.");
        }
        if (userRepository.existsById(uid)) {
            throw new ConflictException("UID_ALREADY_IN_USE", "A user account already exists with this Firebase UID.");
        }
        if (email == null || email.isBlank()) {
            throw new BadRequestException("EMAIL_REQUIRED", "Email is required to register an organization.");
        }

        Organization org = Organization.builder()
                .id(uid)
                .email(email)
                .name(name)
                .build();

        Organization saved = organizationRepository.save(org);
        return toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public OrganizationDataResponse login(String uid) {
        Organization org = organizationRepository.findById(uid)
                .orElseThrow(() -> new NotFoundException("ORGANIZATION_NOT_FOUND",
                        "Organization record not found. Please register."));
        return toResponse(org);
    }

    @Override
    public void logout(String uid) {
        try {
            FirebaseAuth.getInstance().revokeRefreshTokens(uid);
        } catch (FirebaseAuthException e) {
            log.error("Failed to revoke refresh tokens for uid: {}", uid, e);
            throw new UnauthorizedException("LOGOUT_FAILED", "Failed to revoke session.");
        }
    }

    @Override
    @Transactional
    public OrganizationDataResponse update(String uid, OrganizationUpdateRequest request) {
        Organization org = organizationRepository.findById(uid)
                .orElseThrow(() -> new NotFoundException("ORGANIZATION_NOT_FOUND",
                        "Organization record not found. Please register."));

        boolean hasUpdates = false;

        if (request.name() != null && !request.name().trim().isEmpty()) {
            org.setName(request.name().trim());
            hasUpdates = true;
        }

        if (request.description() != null && !request.description().trim().isEmpty()) {
            org.setDescription(request.description().trim());
            hasUpdates = true;
        }

        if (request.avatarUrl() != null && !request.avatarUrl().trim().isEmpty()) {
            org.setAvatarUrl(request.avatarUrl().trim());
            hasUpdates = true;
        }

        if (!hasUpdates) {
            throw new BadRequestException("INVALID_REQUEST", "Request body must contain at least one field to update.");
        }

        Organization saved = organizationRepository.save(org);
        return toResponse(saved);
    }

    private OrganizationDataResponse toResponse(Organization org) {
        return new OrganizationDataResponse(
                org.getId(),
                org.getEmail(),
                org.getName(),
                org.getAvatarUrl(),
                org.getDescription(),
                org.getEstablishedAt() != null ? org.getEstablishedAt().toString() : null,
                org.getNoOfStudents(),
                org.getNoOfCourses(),
                org.getNoOfTeams(),
                org.isVerifiedOrg()
        );
    }
}
