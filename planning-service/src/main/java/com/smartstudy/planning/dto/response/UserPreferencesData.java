package com.smartstudy.planning.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Mirrors identity-service's {@code PreferencesData} payload.
 * Days arrive in the short form the identity {@code WeekDay} enum serializes to
 * ("Mon", "Tue", ...), which is exactly what the scheduler's preferred-days
 * parser expects.
 */
public record UserPreferencesData(
        @JsonProperty("daily_study_hours") Integer dailyStudyHours,
        @JsonProperty("available_days") List<String> availableDays
) {}
