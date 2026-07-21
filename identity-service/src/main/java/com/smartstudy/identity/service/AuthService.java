package com.smartstudy.identity.service;

import com.smartstudy.identity.dto.request.HandshakeRequest;
import com.smartstudy.identity.dto.response.HandshakeResponse;

public interface AuthService {
    HandshakeResponse handshake(String token, HandshakeRequest request);
}
