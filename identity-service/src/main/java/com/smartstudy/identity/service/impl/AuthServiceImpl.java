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
import com.smartstudy.identity.util.FieldMappingUtil;
import com.smartstudy.shared.exception.BadRequestException;
import com.smartstudy.shared.exception.NotFoundException;
import com.smartstudy.shared.exception.UnauthorizedException;
import com.smartstudy.shared.logging.LoggerFactory;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthServiceImpl.class);
    private final UserRepository userRepository;
    private final UserMapper userMapper;

    @Override
    @Transactional
    public HandshakeResult handshake(String token, HandshakeRequest request) {
        if (token == null || !token.startsWith("Bearer ")) {
            throw new UnauthorizedException("MISSING_TOKEN", "Authorization header is missing or malformed.");
        }

        String idToken = token.substring(7);
        FirebaseToken firebaseToken;
        try {
            firebaseToken = FirebaseAuth.getInstance().verifyIdToken(idToken);
        } catch (FirebaseAuthException e) {
            log.warn("Handshake: Firebase token verification failed", e);
            throw new UnauthorizedException("INVALID_TOKEN", "Firebase ID token is invalid or expired.");
        }

        String uid = firebaseToken.getUid();
        String email = firebaseToken.getEmail();
        String avatarUrl = firebaseToken.getPicture();

        // Map and validate request fields
        String name = request.name();
        String appearance = FieldMappingUtil.appearanceToInternal(request.appearance());
        if (appearance == null) {
            throw new BadRequestException("INVALID_APPEARANCE",
                    "Appearance must be one of: " + FieldMappingUtil.validAppearanceValues()
                    + " or their internal equivalents (DARK, LIGHT, SYSTEM).");
        }
        String language = FieldMappingUtil.languageToInternal(request.language());
        if (language == null) {
            throw new BadRequestException("INVALID_LANGUAGE",
                    "Language must be one of: " + FieldMappingUtil.validLanguageValues()
                    + " or their internal equivalents (en, ar).");
        }

        // 1. Primary lookup: find existing user by Firebase UID
        var existingById = userRepository.findById(uid);
        if (existingById.isPresent()) {
            log.info("Handshake: existing user found by UID - uid: {}", uid);
            User user = existingById.get();
            updateUserFields(user, name, avatarUrl, appearance, language);
            userRepository.save(user);
            return new HandshakeResult(userMapper.toHandshakeResponse(user), false);
        }

        // 2. Fallback lookup: find existing user by email
        //    Handles the case where the same person authenticates via a different
        //    Firebase auth provider (different UID, same email).
        var existingByEmail = userRepository.findByEmail(email);
        if (existingByEmail.isPresent()) {
            log.info("Handshake: existing user found by email - uid: {}, email: {}", uid, email);
            User user = existingByEmail.get();
            user.setId(uid);
            updateUserFields(user, name, avatarUrl, appearance, language);
            userRepository.save(user);
            return new HandshakeResult(userMapper.toHandshakeResponse(user), false);
        }

        // 3. No existing user found — create a new one
        log.info("Handshake: creating new user - uid: {}, email: {}", uid, email);
        User newUser = User.builder()
                .id(uid)
                .email(email)
                .name(name)
                .avatarUrl(avatarUrl)
                .appearance(appearance)
                .language(language)
                .build();
        userRepository.save(newUser);
        return new HandshakeResult(userMapper.toHandshakeResponse(newUser), true);
    }

    @Override
    @Transactional(readOnly = true)
    public HandshakeResponse getMe(String uid) {
        User user = userRepository.findById(uid)
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND",
                        "The authenticated user does not exist in the application database."));
        return userMapper.toHandshakeResponse(user);
    }

    private void updateUserFields(User user, String name, String avatarUrl, String appearance, String language) {
        if (name != null) {
            user.setName(name);
        }
        if (avatarUrl != null) {
            user.setAvatarUrl(avatarUrl);
        }
        if (appearance != null) {
            user.setAppearance(appearance);
        }
        if (language != null) {
            user.setLanguage(language);
        }
    }
}
