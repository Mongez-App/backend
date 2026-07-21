package com.smartstudy.planning.service;

import com.smartstudy.planning.dto.request.CalendarConnectRequest;
import com.smartstudy.planning.dto.response.CalendarConnectResponse;
import com.smartstudy.planning.dto.response.CalendarStatusResponse;

public interface CalendarIntegrationService {
    CalendarConnectResponse connect(String userId, CalendarConnectRequest request);
    CalendarStatusResponse getStatus(String userId);
    void disconnect(String userId);
}
