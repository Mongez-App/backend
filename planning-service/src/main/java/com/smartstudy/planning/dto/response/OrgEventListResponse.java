package com.smartstudy.planning.dto.response;

import java.util.List;

public record OrgEventListResponse(
        String teamId,
        List<OrgEventResponse> events,
        int total
) {
}
