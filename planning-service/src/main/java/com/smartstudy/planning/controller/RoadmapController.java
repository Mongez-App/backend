package com.smartstudy.planning.controller;

import com.smartstudy.planning.dto.response.RoadmapResponse;
import com.smartstudy.planning.service.RoadmapService;
import com.smartstudy.shared.logging.LoggerFactory;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/roadmap")
@RequiredArgsConstructor
public class RoadmapController {

    private static final Logger log = LoggerFactory.getLogger(RoadmapController.class);
    private final RoadmapService roadmapService;

    @GetMapping("/weekly")
    public RoadmapResponse getWeeklyRoadmap(
            @RequestHeader("X-User-Id") String userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate) {
        log.info("Incoming request: GET /roadmap/weekly | userId: {}", userId);
        return roadmapService.getWeeklyRoadmap(userId, startDate != null ? startDate : LocalDate.now());
    }

    @PostMapping("/reschedule")
    public RoadmapResponse reschedule(
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader(value = "X-Daily-Study-Minutes", defaultValue = "60") int dailyStudyMinutes,
            @RequestHeader(value = "X-Preferred-Days", defaultValue = "MON,TUE,WED,THU,FRI,SAT,SUN") String preferredDays) {
        log.info("Incoming request: POST /roadmap/reschedule | userId: {}", userId);
        return roadmapService.reschedule(userId, dailyStudyMinutes, preferredDays);
    }
}
