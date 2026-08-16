package com.smartstudy.identity.service.impl;

import com.smartstudy.identity.dto.request.OrgProfileUpdateRequest;
import com.smartstudy.identity.dto.response.OrgProfileResponse;
import com.smartstudy.identity.dto.response.OrgProfileUpdateResponse;
import com.smartstudy.identity.model.Organization;
import com.smartstudy.identity.repository.OrganizationRepository;
import com.smartstudy.identity.service.OrganizationProfileService;
import com.smartstudy.shared.exception.BadRequestException;
import com.smartstudy.shared.exception.NotFoundException;
import com.smartstudy.shared.logging.LoggerFactory;
import org.slf4j.Logger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Profile screen of the organization app. The photo itself is uploaded to
 * planning-service ({@code POST /organization/uploadProfilePhoto}); what is
 * stored here is only the URL that upload returned.
 */
@Service
public class OrganizationProfileServiceImpl implements OrganizationProfileService {

    private static final Logger log = LoggerFactory.getLogger(OrganizationProfileServiceImpl.class);

    private final OrganizationRepository organizationRepository;

    public OrganizationProfileServiceImpl(OrganizationRepository organizationRepository) {
        this.organizationRepository = organizationRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public OrgProfileResponse getProfile(String uid) {
        log.info("Fetching profile for organization {}", uid);
        Organization org = requireOrganization(uid);
        return new OrgProfileResponse(org.getId(), org.getName(), org.getAvatarUrl(), org.getEmail());
    }

    @Override
    @Transactional
    public OrgProfileUpdateResponse updateProfile(String uid, OrgProfileUpdateRequest request) {
        log.info("Updating profile for organization {}", uid);
        Organization org = requireOrganization(uid);

        if (request.name() == null && request.photoUrl() == null) {
            throw new BadRequestException("ValidationError",
                    "Request body must contain name or photoUrl.");
        }
        if (request.name() != null) {
            if (request.name().isBlank()) {
                throw new BadRequestException("ValidationError", "name must not be empty.");
            }
            org.setName(request.name().trim());
        }
        if (request.photoUrl() != null) {
            // Blank clears the photo, which is how the client removes one.
            org.setAvatarUrl(request.photoUrl().isBlank() ? null : request.photoUrl().trim());
        }

        Organization saved = organizationRepository.save(org);
        return new OrgProfileUpdateResponse(
                saved.getId(), saved.getName(), saved.getAvatarUrl(), saved.getUpdatedAt());
    }

    private Organization requireOrganization(String uid) {
        return organizationRepository.findById(uid)
                .orElseThrow(() -> new NotFoundException("NotFound",
                        "Organization record not found. Please register."));
    }
}
