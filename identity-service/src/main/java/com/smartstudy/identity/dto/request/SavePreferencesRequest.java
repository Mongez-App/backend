package com.smartstudy.identity.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.smartstudy.identity.enums.WeekDay;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record SavePreferencesRequest(
        @NotNull(message = "daily_study_hours is required.")
        @Min(value = 0, message = "daily_study_hours must be between 0 and 24.")
        @Max(value = 24, message = "daily_study_hours must be between 0 and 24.")
        @JsonProperty("daily_study_hours") Integer dailyStudyHours,

        @NotNull(message = "available_days must not be null.")
        @JsonProperty("available_days") List<WeekDay> availableDays
) {}
