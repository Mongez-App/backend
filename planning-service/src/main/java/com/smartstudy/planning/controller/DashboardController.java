package com.smartstudy.planning.controller;

import com.smartstudy.planning.dto.response.DashboardResponse;
import com.smartstudy.planning.service.DashboardService;
import com.smartstudy.shared.logging.LoggerFactory;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/home")
@RequiredArgsConstructor
public class DashboardController {

    private static final Logger log = LoggerFactory.getLogger(DashboardController.class);
    private final DashboardService dashboardService;

    @GetMapping("/dashboard")
    public DashboardResponse getDashboard(@RequestHeader("X-User-Id") String userId) {
        log.info("Incoming request: GET /home/dashboard | userId: {}", userId);
        return dashboardService.getDashboard(userId);
    }

    @GetMapping("/deadlines")
    public List<DashboardResponse.DeadlineResponse> getAllUpcomingDeadlines(@RequestHeader("X-User-Id") String userId) {
        log.info("Incoming request: GET /home/deadlines | userId: {}", userId);
        return dashboardService.getAllUpcomingDeadlines(userId);
    }
}
