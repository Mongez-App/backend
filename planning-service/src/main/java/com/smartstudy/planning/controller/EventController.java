package com.smartstudy.planning.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.smartstudy.planning.dto.request.CreateEventRequest;
import com.smartstudy.planning.dto.response.EventsResponse;
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
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/v1/calendar/events")
@RequiredArgsConstructor
public class EventController {

    private static final Logger log = LoggerFactory.getLogger(EventController.class);
    private final EventService eventService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EventsResponse createEvents(
            @RequestHeader("Authorization") String authorization,
            @RequestBody JsonNode payload) {

        String userId = extractUserId(authorization);
        log.info("Incoming request: POST /api/v1/calendar/events | userId: {}", userId);

        List<CreateEventRequest> requests = parsePayload(payload);
        return eventService.createEvents(userId, requests);
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

    private List<CreateEventRequest> parsePayload(JsonNode payload) {
        if (payload == null || payload.isNull()) {
            throw new ValidationException("Request body is required.", List.of("Request body is required."));
        }

        if (payload.has("events") && payload.get("events").isArray()) {
            List<CreateEventRequest> requests = new ArrayList<>();
            for (JsonNode node : payload.get("events")) {
                requests.add(parseSingleEvent(node));
            }
            return requests;
        }

        return List.of(parseSingleEvent(payload));
    }

    private CreateEventRequest parseSingleEvent(JsonNode node) {
        String title = node.has("title") ? node.get("title").asText(null) : null;
        String startDate = node.has("startDate") ? node.get("startDate").asText(null) : null;
        String endDate = node.has("endDate") && !node.get("endDate").isNull() ? node.get("endDate").asText(null) : null;

        return new CreateEventRequest(title, startDate, endDate);
    }
}