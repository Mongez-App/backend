package com.smartstudy.identity.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;

public record UpdateCalendarSyncRequest(
        @JsonProperty("calendar_connected") Boolean calendarConnected,
        @JsonProperty("calendar_synced") Boolean calendarSynced
) {}
