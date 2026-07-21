package com.smartstudy.planning.controller;

import com.smartstudy.planning.dto.response.DashboardResponse;
import com.smartstudy.planning.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/home")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/dashboard")
    public DashboardResponse getDashboard(@RequestHeader("X-User-Id") String userId) {
        return dashboardService.getDashboard(userId);
    }
}
