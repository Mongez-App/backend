package com.smartstudy.identity.controller;

import com.smartstudy.identity.client.PlanningServiceClient;
import com.smartstudy.identity.dto.request.CalendarConnectRequest;
import com.smartstudy.identity.dto.request.HandshakeRequest;
import com.smartstudy.identity.dto.response.CalendarConnectResponse;
import com.smartstudy.identity.dto.response.CalendarStatusResponse;
import com.smartstudy.identity.dto.response.HandshakeResponse;
import com.smartstudy.identity.service.AuthService;
import com.smartstudy.shared.logging.LoggerFactory;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);
    private final AuthService authService;
    private final PlanningServiceClient planningServiceClient;

    private String getFirebaseUid() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof com.google.firebase.auth.FirebaseToken token) {
            return token.getUid();
        }
        throw new com.smartstudy.shared.exception.UnauthorizedException("INVALID_TOKEN", "Authentication is missing or invalid.");
    }

    @PostMapping("/handshake")
    public ResponseEntity<HandshakeResponse> handshake(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody HandshakeRequest request) {
        log.info("Incoming request: POST /auth/handshake");
        HandshakeResponse response = authService.handshake(authorization, request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/calendar/status")
    public ResponseEntity<CalendarStatusResponse> getCalendarStatus() {
        log.info("Incoming request: GET /auth/calendar/status");
        String uid = getFirebaseUid();
        return ResponseEntity.ok(planningServiceClient.getCalendarStatus(uid));
    }

    @PostMapping("/calendar/connect")
    public ResponseEntity<CalendarConnectResponse> connectCalendar() {
        log.info("Incoming request: POST /auth/calendar/connect");
        String uid = getFirebaseUid();
        CalendarConnectRequest request = new CalendarConnectRequest("mock_auth_code", "google_calendar", "https://localhost/callback");
        return ResponseEntity.ok(planningServiceClient.connectCalendar(uid, request));
    }

    @DeleteMapping("/calendar/disconnect")
    public ResponseEntity<Void> disconnectCalendar() {
        log.info("Incoming request: DELETE /auth/calendar/disconnect");
        String uid = getFirebaseUid();
        planningServiceClient.disconnectCalendar(uid);
        return ResponseEntity.noContent().build();
    }
}
