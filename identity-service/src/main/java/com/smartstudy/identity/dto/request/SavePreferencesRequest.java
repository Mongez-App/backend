package com.smartstudy.identity.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.smartstudy.identity.enums.WeekDay;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record SavePreferencesRequest(
        @NotNull
        @Min(value = 1, message = "daily_study_hours must be between 1 and 24.")
        @Max(value = 24, message = "daily_study_hours must be between 1 and 24.")
        @JsonProperty("daily_study_hours") Integer dailyStudyHours,
        
        @NotEmpty(message = "available_days must be a non-empty list of valid weekdays.")
        @JsonProperty("available_days") List<WeekDay> availableDays
) {}
