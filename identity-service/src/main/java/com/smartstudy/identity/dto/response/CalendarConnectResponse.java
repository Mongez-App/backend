package com.smartstudy.identity.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

public record CalendarConnectResponse(
        @JsonProperty("connected") boolean connected,
        @JsonProperty("provider") String provider,
        @JsonProperty("connected_at") Instant connectedAt
) {}
