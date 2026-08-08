package com.smartstudy.identity.service;

import com.smartstudy.identity.dto.request.HandshakeRequest;
import com.smartstudy.identity.dto.request.UpdateCalendarSyncRequest;
import com.smartstudy.identity.dto.response.CalendarSyncResponse;
import com.smartstudy.identity.dto.response.HandshakeResponse;

public interface AuthService {

    record HandshakeResult(HandshakeResponse response, boolean isNewUser) {}

    HandshakeResult handshake(String token, HandshakeRequest request);

    HandshakeResponse getMe(String uid);

    CalendarSyncResponse getCalendarSync(String uid);

    CalendarSyncResponse updateCalendarSync(String uid, UpdateCalendarSyncRequest request);
}
