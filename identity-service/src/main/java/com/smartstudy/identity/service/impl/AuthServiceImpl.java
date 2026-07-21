package com.smartstudy.identity.service.impl;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import com.smartstudy.identity.dto.UserMapper;
import com.smartstudy.identity.dto.request.HandshakeRequest;
import com.smartstudy.identity.dto.response.HandshakeResponse;
import com.smartstudy.identity.model.User;
import com.smartstudy.identity.repository.UserRepository;
import com.smartstudy.identity.service.AuthService;
import com.smartstudy.shared.exception.UnauthorizedException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;

    @Override
    @Transactional
    public HandshakeResponse handshake(String token, HandshakeRequest request) {
        if (token == null || !token.startsWith("Bearer ")) {
            throw new UnauthorizedException("MISSING_TOKEN", "Authorization header is missing or malformed.");
        }

        String idToken = token.substring(7);
        FirebaseToken firebaseToken;
        try {
            firebaseToken = FirebaseAuth.getInstance().verifyIdToken(idToken);
        } catch (FirebaseAuthException e) {
            throw new UnauthorizedException("INVALID_TOKEN", "Firebase ID token is invalid or expired.");
        }

        String uid = firebaseToken.getUid();
        String email = firebaseToken.getEmail();
        String name = firebaseToken.getName();
        String avatarUrl = firebaseToken.getPicture();

        return userRepository.findById(uid)
                .map(existingUser -> {
                    return userMapper.toHandshakeResponse(existingUser, false);
                })
                .orElseGet(() -> {
                    User newUser = User.builder()
                            .id(uid)
                            .email(email)
                            .name(name)
                            .avatarUrl(avatarUrl)
                            .isGuest(request.isGuest() != null ? request.isGuest() : false)
                            .build();
                    userRepository.save(newUser);
                    return userMapper.toHandshakeResponse(newUser, true);
                });
    }
}
