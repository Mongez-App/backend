package com.smartstudy.planning.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public record InternalProfileStatsResponse(
        @JsonProperty("total_study_hours") int totalStudyHours,
        @JsonProperty("completed_tasks_count") int completedTasksCount,
        @JsonProperty("current_streak_days") int currentStreakDays
) {}
