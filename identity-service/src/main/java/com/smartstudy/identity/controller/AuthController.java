package com.smartstudy.identity.controller;

import com.smartstudy.identity.dto.request.HandshakeRequest;
import com.smartstudy.identity.dto.response.HandshakeResponse;
import com.smartstudy.identity.service.AuthService;
import com.smartstudy.identity.service.AuthService.HandshakeResult;
import com.smartstudy.shared.logging.LoggerFactory;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
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

    private String getFirebaseUid() {
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            throw new com.smartstudy.shared.exception.UnauthorizedException("INVALID_TOKEN", "Authentication is missing or invalid.");
        }
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof com.google.firebase.auth.FirebaseToken token) {
            return token.getUid();
        }
        if (principal instanceof String uid && !uid.isBlank()) {
            return uid;
        }
        throw new com.smartstudy.shared.exception.UnauthorizedException("INVALID_TOKEN", "Authentication is missing or invalid.");
    }

    @PostMapping("/handshake")
    public ResponseEntity<HandshakeResponse> handshake(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody HandshakeRequest request) {
        log.info("Incoming request: POST /auth/handshake");
        HandshakeResult result = authService.handshake(authorization, request);
        if (result.isNewUser()) {
            return ResponseEntity.status(HttpStatus.CREATED).body(result.response());
        }
        return ResponseEntity.ok(result.response());
    }

    @GetMapping("/me")
    public ResponseEntity<HandshakeResponse> getMe() {
        log.info("Incoming request: GET /auth/me");
        String uid = getFirebaseUid();
        return ResponseEntity.ok(authService.getMe(uid));
    }
}
