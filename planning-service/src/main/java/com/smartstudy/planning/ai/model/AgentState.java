package com.smartstudy.planning.ai.model;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class AgentState {
    public String userId;
    public java.util.UUID courseId;
    public java.util.UUID materialId;
    public int dailyStudyMinutes;
    public String preferredDays;
    public boolean isIncremental;
    public List<ExtractedTask> tasks;
    public LocalDate examDate;
    public List<AvailableSlot> slots;
    public ScheduleResult scheduleResult;
    public List<String> executionLog;
    public int iteration;
    public String error;
    public int consecutiveMalformedDecisions;

    public AgentState(String userId, UUID courseId, UUID materialId,
                      int dailyStudyMinutes, String preferredDays, boolean isIncremental) {
        this.userId = userId;
        this.courseId = courseId;
        this.materialId = materialId;
        this.dailyStudyMinutes = dailyStudyMinutes;
        this.preferredDays = preferredDays;
        this.isIncremental = isIncremental;
        this.tasks = new ArrayList<>();
        this.slots = new ArrayList<>();
        this.executionLog = new ArrayList<>();
        this.iteration = 0;
        this.consecutiveMalformedDecisions = 0;
    }

    public void log(String message) {
        this.executionLog.add(message);
    }

    public boolean isTasksExtracted() {
        return !tasks.isEmpty();
    }

    public boolean areSlotsQueried() {
        return !slots.isEmpty();
    }

    public boolean isScheduled() {
        return scheduleResult != null;
    }
}
