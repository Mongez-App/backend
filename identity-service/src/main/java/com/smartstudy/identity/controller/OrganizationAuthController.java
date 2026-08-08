package com.smartstudy.identity.controller;

import com.smartstudy.identity.dto.request.OrganizationRegisterRequest;
import com.smartstudy.identity.dto.request.OrganizationUpdateRequest;
import com.smartstudy.identity.dto.response.OrganizationAuthResponse;
import com.smartstudy.identity.dto.response.OrganizationDataResponse;
import com.smartstudy.identity.dto.response.OrganizationLogoutResponse;
import com.smartstudy.identity.service.OrganizationAuthService;
import com.smartstudy.shared.logging.LoggerFactory;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/organizations/auth")
@RequiredArgsConstructor
public class OrganizationAuthController {

    private static final Logger log = LoggerFactory.getLogger(OrganizationAuthController.class);
    private final OrganizationAuthService organizationAuthService;

    @PostMapping("/register")
    public ResponseEntity<OrganizationAuthResponse> register(
            @Valid @RequestBody OrganizationRegisterRequest request,
            @RequestHeader("X-User-Id") String uid,
            @RequestHeader("X-User-Email") String email) {
        log.info("Incoming request: POST /organizations/auth/register");
        OrganizationDataResponse data = organizationAuthService.register(uid, email, request.name());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new OrganizationAuthResponse("Organization registered successfully", data));
    }

    @PostMapping("/login")
    public ResponseEntity<OrganizationAuthResponse> login(
            @RequestHeader("X-User-Id") String uid) {
        log.info("Incoming request: POST /organizations/auth/login");
        OrganizationDataResponse data = organizationAuthService.login(uid);
        return ResponseEntity.ok(new OrganizationAuthResponse("Authentication successful", data));
    }

    @PostMapping("/logout")
    public ResponseEntity<OrganizationLogoutResponse> logout(
            @RequestHeader("X-User-Id") String uid) {
        log.info("Incoming request: POST /organizations/auth/logout");
        organizationAuthService.logout(uid);
        return ResponseEntity.ok(new OrganizationLogoutResponse("Successfully logged out and session revoked."));
    }

    @PatchMapping("/update")
    public ResponseEntity<OrganizationAuthResponse> update(
            @RequestHeader("X-User-Id") String uid,
            @Valid @RequestBody OrganizationUpdateRequest request) {
        log.info("Incoming request: PATCH /organizations/auth/update");
        OrganizationDataResponse data = organizationAuthService.update(uid, request);
        return ResponseEntity.ok(new OrganizationAuthResponse("Organization updated successfully", data));
    }
}
