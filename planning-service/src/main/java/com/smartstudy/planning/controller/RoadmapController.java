package com.smartstudy.planning.controller;

import com.smartstudy.planning.dto.response.RoadmapResponse;
import com.smartstudy.planning.service.RoadmapService;
import com.smartstudy.planning.service.UserPreferencesService;
import com.smartstudy.shared.exception.BadRequestException;
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
    private final UserPreferencesService userPreferencesService;

    @GetMapping("/weekly")
    public RoadmapResponse getWeeklyRoadmap(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestParam(name = "start_date", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate) {
        validateUserId(userId);
        log.info("Incoming request: GET /roadmap/weekly | userId: {} | start_date: {}", userId, startDate);
        return roadmapService.getWeeklyRoadmap(userId, startDate != null ? startDate : LocalDate.now());
    }

    @PostMapping("/reschedule")
    public RoadmapResponse reschedule(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-Daily-Study-Minutes", defaultValue = "60") int dailyStudyMinutes,
            @RequestHeader(value = "X-Preferred-Days", defaultValue = "MON,TUE,WED,THU,FRI,SAT,SUN") String preferredDays) {
        validateUserId(userId);
        log.info("Incoming request: POST /roadmap/reschedule | userId: {}", userId);
        UserPreferencesService.StudyPreferences preferences =
                userPreferencesService.resolve(userId, dailyStudyMinutes, preferredDays);
        return roadmapService.reschedule(userId, preferences.dailyStudyMinutes(), preferences.preferredDays());
    }

    private void validateUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new BadRequestException("MISSING_USER_ID", "X-User-Id header is required.");
        }
    }
}
