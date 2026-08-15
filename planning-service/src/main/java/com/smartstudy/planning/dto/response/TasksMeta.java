package com.smartstudy.planning.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TasksMeta(
        @JsonProperty("preferred_study_time_minutes") int preferredStudyTimeMinutes,
        @JsonProperty("total_tasks") int totalTasks
) {
}
