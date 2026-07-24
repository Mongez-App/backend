package com.smartstudy.identity.service;

import com.smartstudy.identity.dto.request.HandshakeRequest;
import com.smartstudy.identity.dto.response.HandshakeResponse;

public interface AuthService {

    record HandshakeResult(HandshakeResponse response, boolean isNewUser) {}

    HandshakeResult handshake(String token, HandshakeRequest request);

    HandshakeResponse getMe(String uid);
}
