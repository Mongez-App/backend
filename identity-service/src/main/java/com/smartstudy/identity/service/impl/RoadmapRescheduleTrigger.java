package com.smartstudy.identity.service.impl;

import com.smartstudy.identity.client.PlanningServiceClient;
import com.smartstudy.identity.enums.WeekDay;
import com.smartstudy.shared.logging.LoggerFactory;
import org.slf4j.Logger;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Fire-and-forget call to planning-service that asks it to re-plan the user's
 * roadmap after preference changes. Lives in its own bean so Spring's @Async
 * proxy actually intercepts the call: invoked from inside UserServiceImpl it
 * would otherwise run synchronously on the request thread.
 */
@Component
public class RoadmapRescheduleTrigger {

    private static final Logger log = LoggerFactory.getLogger(RoadmapRescheduleTrigger.class);

    private final PlanningServiceClient planningServiceClient;

    public RoadmapRescheduleTrigger(PlanningServiceClient planningServiceClient) {
        this.planningServiceClient = planningServiceClient;
    }

    @Async
    public void triggerRoadmapReschedule(String userId, Integer dailyStudyHours, List<WeekDay> availableDays) {
        int minutes = dailyStudyHours != null ? dailyStudyHours * 60 : 0;
        String daysCsv = availableDays == null || availableDays.isEmpty()
                ? "MON,TUE,WED,THU,FRI,SAT,SUN"
                : availableDays.stream()
                .map(d -> d.getValue().toUpperCase())
                .collect(Collectors.joining(","));
        try {
            planningServiceClient.rescheduleRoadmap(userId, minutes, daysCsv);
        } catch (Exception ex) {
            log.warn("Roadmap reschedule failed for user {}: {}", userId, ex.getMessage());
        }
    }
}
