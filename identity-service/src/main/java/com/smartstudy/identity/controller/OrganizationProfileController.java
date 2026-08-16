package com.smartstudy.identity.controller;

import com.smartstudy.identity.dto.request.OrgProfileUpdateRequest;
import com.smartstudy.identity.dto.response.OrgProfileResponse;
import com.smartstudy.identity.dto.response.OrgProfileUpdateResponse;
import com.smartstudy.identity.service.OrganizationProfileService;
import com.smartstudy.shared.exception.UnauthorizedException;
import com.smartstudy.shared.logging.LoggerFactory;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Organization profile endpoints of the org API contract. The rest of
 * {@code /organization/**} is served by planning-service; the gateway routes
 * these two paths here because the organization record lives in this service.
 */
@RestController
@RequestMapping("/organization")
@RequiredArgsConstructor
public class OrganizationProfileController {

    private static final Logger log = LoggerFactory.getLogger(OrganizationProfileController.class);
    private final OrganizationProfileService organizationProfileService;

    @GetMapping("/getProfile")
    public ResponseEntity<OrgProfileResponse> getProfile(
            @RequestHeader(value = "X-User-Id", required = false) String uid) {
        log.info("Incoming request: GET /organization/getProfile");
        return ResponseEntity.ok(organizationProfileService.getProfile(requireUid(uid)));
    }

    @PostMapping("/updateProfile")
    public ResponseEntity<OrgProfileUpdateResponse> updateProfile(
            @RequestHeader(value = "X-User-Id", required = false) String uid,
            @RequestBody OrgProfileUpdateRequest request) {
        log.info("Incoming request: POST /organization/updateProfile");
        return ResponseEntity.ok(organizationProfileService.updateProfile(requireUid(uid), request));
    }

    private String requireUid(String uid) {
        if (uid == null || uid.isBlank()) {
            throw new UnauthorizedException("Unauthorized", "Firebase ID token is missing or invalid.");
        }
        return uid;
    }
}
