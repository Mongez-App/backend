package com.smartstudy.identity.controller;

import com.smartstudy.identity.dto.request.CreateUserRequest;
import com.smartstudy.identity.dto.request.UpdateProfileRequest;
import com.smartstudy.identity.dto.response.ProfileResponse;
import com.smartstudy.identity.dto.response.UpdateProfileResponse;
import com.smartstudy.identity.dto.response.UserResponse;
import com.smartstudy.identity.service.UserService;
import com.smartstudy.shared.logging.LoggerFactory;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private static final Logger log = LoggerFactory.getLogger(UserController.class);
    private final UserService userService;

    private String getFirebaseUid() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof com.google.firebase.auth.FirebaseToken token) {
            return token.getUid();
        }
        throw new com.smartstudy.shared.exception.UnauthorizedException("INVALID_TOKEN", "Authentication is missing or invalid.");
    }

    @GetMapping("/me/profile")
    public ResponseEntity<ProfileResponse> getProfile() {
        log.info("Incoming request: GET /users/me/profile");
        String uid = getFirebaseUid();
        return ResponseEntity.ok(userService.getProfile(uid));
    }

    @PatchMapping("/me/profile")
    public ResponseEntity<UpdateProfileResponse> updateProfile(
            @Valid @RequestBody UpdateProfileRequest request) {
        log.info("Incoming request: PATCH /users/me/profile");
        String uid = getFirebaseUid();
        return ResponseEntity.ok(userService.updateProfile(uid, request));
    }

    @PutMapping("/me/preferences")
    public ResponseEntity<com.smartstudy.identity.dto.response.PreferencesResponse> savePreferences(
            @Valid @RequestBody com.smartstudy.identity.dto.request.SavePreferencesRequest request) {
        log.info("Incoming request: PUT /users/me/preferences");
        String uid = getFirebaseUid();
        return ResponseEntity.ok(userService.savePreferences(uid, request));
    }

    @DeleteMapping("/me")
    public ResponseEntity<Void> deleteUser() {
        log.info("Incoming request: DELETE /users/me");
        String uid = getFirebaseUid();
        userService.deleteUser(uid);
        return ResponseEntity.noContent().build();
    }

    @PostMapping
    public ResponseEntity<UserResponse> createUser(
            @Valid @RequestBody CreateUserRequest request) {
        log.info("Incoming request: POST /users");
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.createUser(request));
    }

    @GetMapping
    public ResponseEntity<List<UserResponse>> getAllUsers() {
        log.info("Incoming request: GET /users");
        return ResponseEntity.ok(userService.getAllUsers());
    }
}
