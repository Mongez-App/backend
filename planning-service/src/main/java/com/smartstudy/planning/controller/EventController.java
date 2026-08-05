package com.smartstudy.planning.controller;

import com.smartstudy.planning.dto.request.CreateEventRequest;
import com.smartstudy.planning.dto.request.CreateEventsRequest;
import com.smartstudy.planning.dto.response.EventsResponse;
import com.smartstudy.planning.dto.response.EventResponse;
import com.smartstudy.planning.exception.ValidationException;
import com.smartstudy.planning.service.EventService;
import com.smartstudy.shared.exception.UnauthorizedException;
import com.smartstudy.shared.logging.LoggerFactory;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/v1/calendar/events")
@RequiredArgsConstructor
public class EventController {

    private static final Logger log = LoggerFactory.getLogger(EventController.class);
    private final EventService eventService;

    @GetMapping
    public List<EventResponse> getEvents(
            @RequestHeader("Authorization") String authorization,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endDate) {

        String userId = extractUserId(authorization);
        log.info("Incoming request: GET /api/v1/calendar/events | userId: {}", userId);

        if (startDate != null && endDate != null) {
            return eventService.getEvents(userId, startDate, endDate);
        }

        return eventService.getEvents(userId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EventsResponse createEvents(
            @RequestHeader("Authorization") String authorization,
            @Valid @RequestBody CreateEventsRequest request) {

        String userId = extractUserId(authorization);
        log.info("Incoming request: POST /api/v1/calendar/events | userId: {}", userId);

        return eventService.createEvents(userId, request.events());
    }

    private String extractUserId(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new UnauthorizedException("Invalid or expired user bearer token.");
        }

        String token = authorization.substring(7);
        try {
            if (FirebaseApp.getApps().isEmpty()) {
                throw new UnauthorizedException("Invalid or expired user bearer token.");
            }
            FirebaseToken firebaseToken = FirebaseAuth.getInstance().verifyIdToken(token);
            return firebaseToken.getUid();
        } catch (FirebaseAuthException e) {
            log.warn("Invalid Firebase token: {}", e.getMessage());
            throw new UnauthorizedException("Invalid or expired user bearer token.");
        }
    }
}