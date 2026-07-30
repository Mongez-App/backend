package com.smartstudy.planning.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.smartstudy.planning.model.Priority;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record DashboardResponse(
        @JsonProperty("welcome_message") String welcomeMessage,
        @JsonProperty("today_focus") TodayFocusResponse todayFocus,
        @JsonProperty("progress_metrics") ProgressMetricsResponse progressMetrics,
        @JsonProperty("today_tasks") List<DashboardTaskResponse> todayTasks,
        @JsonProperty("upcoming_deadlines") List<DeadlineResponse> upcomingDeadlines,
        @JsonProperty("streak") StreakResponse streak,
        @JsonProperty("ai_suggestion") AiSuggestionResponse aiSuggestion
) {

    public record TodayFocusResponse(
            @JsonProperty("course_id") UUID courseId,
            @JsonProperty("course_name") String courseName,
            @JsonProperty("image_url") String imageUrl,
            @JsonProperty("allocated_duration") String allocatedDuration,
            @JsonProperty("duration_minutes") Integer durationMinutes
    ) {
    }

    public record ProgressMetricsResponse(
            @JsonProperty("today_completed_tasks") long todayCompletedTasks,
            @JsonProperty("today_total_tasks") long todayTotalTasks,
            @JsonProperty("weekly_hours_completed") long weeklyHoursCompleted,
            @JsonProperty("weekly_hours_goal") long weeklyHoursGoal,
            @JsonProperty("monthly_hours_completed") long monthlyHoursCompleted,
            @JsonProperty("monthly_hours_goal") long monthlyHoursGoal
    ) {
    }

    public record DashboardTaskResponse(
            @JsonProperty("task_id") UUID taskId,
            @JsonProperty("title") String title,
            @JsonProperty("duration_minutes") Integer durationMinutes,
            @JsonProperty("priority") Priority priority,
            @JsonProperty("is_completed") boolean completed
    ) {
    }

    public record DeadlineResponse(
            @JsonProperty("deadline_id") UUID deadlineId,
            @JsonProperty("title") String title,
            @JsonProperty("course_name") String courseName,
            @JsonProperty("event_type") String eventType,
            @JsonProperty("due_text") String dueText,
            @JsonProperty("due_date") Instant dueDate,
            @JsonProperty("days_left") Long daysLeft
    ) {
    }

    public record StreakResponse(
            @JsonProperty("current_streak_days") int currentStreakDays
    ) {
    }

    public record AiSuggestionResponse(
            @JsonProperty("text") String text
    ) {
    }
}
