package com.smartstudy.identity.controller;

import com.smartstudy.identity.dto.request.OrganizationRegisterRequest;
import com.smartstudy.identity.dto.request.OrganizationUpdateRequest;
import com.smartstudy.identity.dto.response.OrganizationAuthResponse;
import com.smartstudy.identity.dto.response.OrganizationDataResponse;
import com.smartstudy.identity.dto.response.OrganizationLogoutResponse;
import com.smartstudy.identity.service.OrganizationAuthService;
import com.smartstudy.shared.exception.UnauthorizedException;
import com.smartstudy.shared.logging.LoggerFactory;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/organization/auth")
@RequiredArgsConstructor
public class OrganizationAuthController {

    private static final Logger log = LoggerFactory.getLogger(OrganizationAuthController.class);
    private final OrganizationAuthService organizationAuthService;

    @PostMapping("/register")
    public ResponseEntity<OrganizationAuthResponse> register(
            @Valid @RequestBody OrganizationRegisterRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String uid,
            @RequestHeader(value = "X-User-Email", required = false) String email) {
        log.info("Incoming request: POST /organization/auth/register");
        // Both headers come from the verified token at the gateway. Declaring
        // them required turns a missing one into a 500; the service reports the
        // absent email as a 400 instead.
        OrganizationDataResponse data = organizationAuthService.register(requireUid(uid), email, request.name());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new OrganizationAuthResponse("Organization registered successfully", data));
    }

    @PostMapping("/login")
    public ResponseEntity<OrganizationAuthResponse> login(
            @RequestHeader(value = "X-User-Id", required = false) String uid) {
        log.info("Incoming request: POST /organization/auth/login");
        OrganizationDataResponse data = organizationAuthService.login(requireUid(uid));
        return ResponseEntity.ok(new OrganizationAuthResponse("Authentication successful", data));
    }

    @PostMapping("/logout")
    public ResponseEntity<OrganizationLogoutResponse> logout(
            @RequestHeader(value = "X-User-Id", required = false) String uid) {
        log.info("Incoming request: POST /organization/auth/logout");
        organizationAuthService.logout(requireUid(uid));
        return ResponseEntity.ok(new OrganizationLogoutResponse("Successfully logged out and session revoked."));
    }

    @PatchMapping("/update")
    public ResponseEntity<OrganizationAuthResponse> update(
            @RequestHeader(value = "X-User-Id", required = false) String uid,
            @Valid @RequestBody OrganizationUpdateRequest request) {
        log.info("Incoming request: PATCH /organization/auth/update");
        OrganizationDataResponse data = organizationAuthService.update(requireUid(uid), request);
        return ResponseEntity.ok(new OrganizationAuthResponse("Organization updated successfully", data));
    }

    private String requireUid(String uid) {
        if (uid == null || uid.isBlank()) {
            throw new UnauthorizedException("Unauthorized", "Firebase ID token is missing or invalid.");
        }
        return uid;
    }
}
