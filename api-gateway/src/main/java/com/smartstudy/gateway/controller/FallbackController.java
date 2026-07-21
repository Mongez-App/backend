package com.smartstudy.gateway.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class FallbackController {

    @RequestMapping("/fallback/identity")
    public ResponseEntity<Map<String, String>> identityFallback() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of(
                        "error", "IDENTITY_SERVICE_UNAVAILABLE",
                        "message", "Identity service is temporarily unavailable"
                ));
    }

    @RequestMapping("/fallback/planning")
    public ResponseEntity<Map<String, String>> planningFallback() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of(
                        "error", "PLANNING_SERVICE_UNAVAILABLE",
                        "message", "Planning service is temporarily unavailable"
                ));
    }
}
