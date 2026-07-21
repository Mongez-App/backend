package com.smartstudy.planning.controller;

import com.smartstudy.planning.dto.request.EndSessionRequest;
import com.smartstudy.planning.dto.request.StartSessionRequest;
import com.smartstudy.planning.dto.response.SessionResponse;
import com.smartstudy.planning.service.SessionService;
import com.smartstudy.shared.logging.LoggerFactory;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/sessions")
@RequiredArgsConstructor
public class SessionController {

    private static final Logger log = LoggerFactory.getLogger(SessionController.class);
    private final SessionService sessionService;

    @PostMapping("/start")
    public SessionResponse startSession(
            @RequestHeader("X-User-Id") String userId,
            @Valid @RequestBody StartSessionRequest request) {
        log.info("Incoming request: POST /sessions/start | userId: {}", userId);
        return sessionService.startSession(userId, request);
    }

    @PostMapping("/{sessionId}/end")
    public SessionResponse endSession(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable UUID sessionId,
            @Valid @RequestBody EndSessionRequest request) {
        log.info("Incoming request: POST /sessions/{}/end | userId: {}", sessionId, userId);
        return sessionService.endSession(userId, sessionId, request);
    }
}
