package com.smartstudy.planning.dto.request;

public record GetEventsRequest(
        String startDate,
        String endDate
) {
}