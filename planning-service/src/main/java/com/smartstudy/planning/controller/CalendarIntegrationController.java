package com.smartstudy.planning.controller;

import com.smartstudy.planning.dto.request.CalendarConnectRequest;
import com.smartstudy.planning.dto.response.CalendarConnectResponse;
import com.smartstudy.planning.dto.response.CalendarStatusResponse;
import com.smartstudy.planning.service.CalendarIntegrationService;
import com.smartstudy.shared.logging.LoggerFactory;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth/calendar")
@RequiredArgsConstructor
public class CalendarIntegrationController {

    private static final Logger log = LoggerFactory.getLogger(CalendarIntegrationController.class);
    private final CalendarIntegrationService calendarIntegrationService;

    @PostMapping("/connect")
    public ResponseEntity<CalendarConnectResponse> connect(
            @RequestHeader("X-User-Id") String userId,
            @Valid @RequestBody CalendarConnectRequest request) {
        log.info("Incoming request: POST /auth/calendar/connect | userId: {}", userId);
        return ResponseEntity.ok(calendarIntegrationService.connect(userId, request));
    }

    @GetMapping("/status")
    public ResponseEntity<CalendarStatusResponse> getStatus(
            @RequestHeader("X-User-Id") String userId) {
        log.info("Incoming request: GET /auth/calendar/status | userId: {}", userId);
        return ResponseEntity.ok(calendarIntegrationService.getStatus(userId));
    }

    @DeleteMapping("/disconnect")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void disconnect(
            @RequestHeader("X-User-Id") String userId) {
        log.info("Incoming request: DELETE /auth/calendar/disconnect | userId: {}", userId);
        calendarIntegrationService.disconnect(userId);
    }
}
