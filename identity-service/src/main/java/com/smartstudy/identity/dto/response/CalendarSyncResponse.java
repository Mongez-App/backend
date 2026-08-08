package com.smartstudy.identity.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

public record CalendarSyncResponse(
        @JsonProperty("calendar_connected") boolean calendarConnected,
        @JsonProperty("calendar_synced") boolean calendarSynced,
        @JsonProperty("last_calendar_sync_at") Instant lastCalendarSyncAt
) {}
