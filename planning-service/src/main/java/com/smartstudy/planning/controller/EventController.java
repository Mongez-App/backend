package com.smartstudy.planning.controller;

import com.smartstudy.planning.dto.request.CreateEventsRequest;
import com.smartstudy.planning.dto.request.GetEventsRequest;
import com.smartstudy.planning.dto.response.EventsResponse;
import com.smartstudy.planning.dto.response.EventResponse;
import com.smartstudy.planning.service.EventService;
import com.smartstudy.shared.exception.BadRequestException;
import com.smartstudy.shared.logging.LoggerFactory;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/calendar/events")
@RequiredArgsConstructor
public class EventController {

    private static final Logger log = LoggerFactory.getLogger(EventController.class);
    private final EventService eventService;

    @GetMapping
    public List<EventResponse> getEvents(
            @RequestHeader("X-User-Id") String userId,
            @ModelAttribute GetEventsRequest request) {

        log.info("Incoming request: GET /calendar/events | userId: {}", userId);
        validateUserId(userId);

        return eventService.getEvents(userId, request);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EventsResponse createEvents(
            @RequestHeader("X-User-Id") String userId,
            @Valid @RequestBody CreateEventsRequest request) {

        log.info("Incoming request: POST /calendar/events | userId: {}", userId);
        validateUserId(userId);

        return eventService.createEvents(userId, request.events());
    }

    private void validateUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new BadRequestException("MISSING_USER_ID", "X-User-Id header is required.");
        }
    }
}