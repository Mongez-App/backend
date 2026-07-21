package com.smartstudy.identity.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.smartstudy.identity.enums.WeekDay;
import java.util.List;

public record PreferencesData(
        @JsonProperty("daily_study_hours") int dailyStudyHours,
        @JsonProperty("available_days") List<WeekDay> availableDays
) {}
