package com.smartstudy.planning.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record DiscoverResponse(
        @JsonProperty("pending_requests") List<PendingRequestResponse> pendingRequests,
        @JsonProperty("trending_teams") List<TrendingTeamResponse> trendingTeams
) {
}
