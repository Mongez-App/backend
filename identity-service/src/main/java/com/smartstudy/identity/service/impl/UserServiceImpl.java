package com.smartstudy.identity.service.impl;

import com.smartstudy.identity.client.PlanningServiceClient;
import com.smartstudy.identity.dto.UserMapper;
import com.smartstudy.identity.dto.request.CreateUserRequest;
import com.smartstudy.identity.dto.request.UpdateProfileRequest;
import com.smartstudy.identity.dto.response.ProfileResponse;
import com.smartstudy.identity.dto.response.ProfileStatsResponse;
import com.smartstudy.identity.dto.response.UpdateProfileResponse;
import com.smartstudy.identity.dto.response.UserResponse;
import com.smartstudy.identity.model.User;
import com.smartstudy.identity.model.UserPreference;
import com.smartstudy.identity.repository.UserPreferenceRepository;
import com.smartstudy.identity.repository.UserRepository;
import com.smartstudy.identity.service.UserService;
import com.smartstudy.shared.exception.BadRequestException;
import com.smartstudy.shared.exception.ConflictException;
import com.smartstudy.shared.exception.NotFoundException;
import com.smartstudy.shared.logging.LoggerFactory;
import com.smartstudy.identity.util.FieldMappingUtil;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private static final Logger log = LoggerFactory.getLogger(UserServiceImpl.class);
    private final UserRepository userRepository;
    private final UserPreferenceRepository userPreferenceRepository;
    private final PlanningServiceClient planningServiceClient;
    private final UserMapper userMapper;

    @Override
    public ProfileResponse getProfile(String firebaseUid) {
        log.info("Fetching profile for uid: {}", firebaseUid);
        User user = userRepository.findById(firebaseUid)
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "User profile not found. Please complete handshake first."));

        ProfileStatsResponse stats = planningServiceClient.getUserStats(firebaseUid);
        return userMapper.toProfileResponse(user, stats);
    }

    @Override
    @Transactional
    public UpdateProfileResponse updateProfile(String firebaseUid, UpdateProfileRequest request) {
        log.info("Updating profile for uid: {}", firebaseUid);
        User user = userRepository.findById(firebaseUid)
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "User not found."));

        boolean hasUpdates = false;

        if (request.name() != null && !request.name().trim().isEmpty()) {
            user.setName(request.name().trim());
            hasUpdates = true;
        }

        if (request.avatarUrl() != null) {
            user.setAvatarUrl(request.avatarUrl().trim());
            hasUpdates = true;
        }

        if (request.appearance() != null) {
            String appearance = FieldMappingUtil.appearanceToInternal(request.appearance());
            if (appearance == null) {
                throw new BadRequestException("INVALID_APPEARANCE",
                        "Appearance must be " + FieldMappingUtil.validAppearanceValues() + ".");
            }
            user.setAppearance(appearance);
            hasUpdates = true;
        }

        if (request.language() != null) {
            String language = FieldMappingUtil.languageToInternal(request.language());
            if (language == null) {
                throw new BadRequestException("INVALID_LANGUAGE",
                        "Language must be " + FieldMappingUtil.validLanguageValues() + ".");
            }
            user.setLanguage(language);
            hasUpdates = true;
        }

        if (!hasUpdates) {
            throw new BadRequestException("INVALID_REQUEST", "Request body must contain at least one field to update.");
        }

        userRepository.save(user);

        return new UpdateProfileResponse("success", userMapper.toProfileDetailsResponse(user));
    }

    @Override
    @Transactional
    public com.smartstudy.identity.dto.response.PreferencesResponse savePreferences(String firebaseUid, com.smartstudy.identity.dto.request.SavePreferencesRequest request) {
        log.info("Saving preferences for uid: {}", firebaseUid);
        if (!userRepository.existsById(firebaseUid)) {
            throw new NotFoundException("USER_NOT_FOUND", "User not found. Please complete handshake first.");
        }

        UserPreference preference = userPreferenceRepository.findById(firebaseUid)
                .orElse(UserPreference.builder().userId(firebaseUid).build());

        preference.setDailyStudyHours(request.dailyStudyHours());
        preference.setAvailableDays(request.availableDays());
        userPreferenceRepository.save(preference);

        return new com.smartstudy.identity.dto.response.PreferencesResponse("success", new com.smartstudy.identity.dto.response.PreferencesData(preference.getDailyStudyHours(), preference.getAvailableDays()));
    }

    @Override
    @Transactional
    public void deleteUser(String firebaseUid) {
        log.warn("Deleting user: {}", firebaseUid);
        User user = userRepository.findById(firebaseUid)
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "User not found."));

userPreferenceRepository.deleteById(firebaseUid);
        userPreferenceRepository.flush();
        userRepository.delete(user);
        userRepository.flush();
    }

    @Override
    @Transactional
    public UserResponse createUser(CreateUserRequest request) {
        log.info("Creating user with email: {}", request.email());
        if (request.email() == null || request.email().trim().isEmpty()) {
            throw new BadRequestException("INVALID_EMAIL", "Email is required.");
        }

        String normalizedEmail = request.email().trim().toLowerCase();

        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new ConflictException("EMAIL_EXISTS", "A user with this email already exists.");
        }

        User user = User.builder()
                .id(UUID.randomUUID().toString())
                .email(normalizedEmail)
                .name(request.name() != null ? request.name().trim() : null)
                .isGuest(request.isGuest() != null ? request.isGuest() : false)
                .build();

        userRepository.save(user);

        return userMapper.toUserResponse(user);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserResponse> getAllUsers() {
        log.info("Fetching all users");
        return userRepository.findAll()
                .stream()
                .map(userMapper::toUserResponse)
                .toList();
    }
}
