package com.smartstudy.identity.service.impl;

import com.smartstudy.identity.client.PlanningServiceClient;
import com.smartstudy.identity.dto.UserMapper;
import com.smartstudy.identity.dto.request.UpdateProfileRequest;
import com.smartstudy.identity.dto.response.ProfileResponse;
import com.smartstudy.identity.dto.response.ProfileStatsResponse;
import com.smartstudy.identity.dto.response.UpdateProfileResponse;
import com.smartstudy.identity.model.User;
import com.smartstudy.identity.model.UserPreference;
import com.smartstudy.identity.repository.UserPreferenceRepository;
import com.smartstudy.identity.repository.UserRepository;
import com.smartstudy.identity.service.UserService;
import com.smartstudy.shared.exception.BadRequestException;
import com.smartstudy.shared.exception.NotFoundException;
import com.smartstudy.identity.util.FieldMappingUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final UserPreferenceRepository userPreferenceRepository;
    private final PlanningServiceClient planningServiceClient;
    private final UserMapper userMapper;
    private final org.springframework.amqp.rabbit.core.RabbitTemplate rabbitTemplate;

    @Override
    public ProfileResponse getProfile(String firebaseUid) {
        User user = userRepository.findById(firebaseUid)
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "User profile not found. Please complete handshake first."));

        ProfileStatsResponse stats = planningServiceClient.getUserStats(firebaseUid);
        return userMapper.toProfileResponse(user, stats);
    }

    @Override
    @Transactional
    public UpdateProfileResponse updateProfile(String firebaseUid, UpdateProfileRequest request) {
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

        if (request.calendarSyncConnected() != null) {
            user.setCalendarSyncConnected(request.calendarSyncConnected());
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
        User user = userRepository.findById(firebaseUid)
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "User not found."));

        try {
            planningServiceClient.disconnectCalendar(firebaseUid);
        } catch (feign.FeignException e) {
            throw new com.smartstudy.shared.exception.BadRequestException("SERVICE_UNAVAILABLE", "Calendar service is currently unavailable.");
        }

        try {
            com.google.firebase.auth.FirebaseAuth auth = com.google.firebase.auth.FirebaseAuth.getInstance();
            boolean userExists = false;
            try {
                auth.getUser(firebaseUid);
                userExists = true;
            } catch (com.google.firebase.auth.FirebaseAuthException e) {
                if (!"user-not-found".equals(e.getErrorCode()) && e.getAuthErrorCode() != com.google.firebase.auth.AuthErrorCode.USER_NOT_FOUND) {
                    throw e;
                }
            }
            
            if (userExists) {
                auth.deleteUser(firebaseUid);
            }
        } catch (com.google.firebase.auth.FirebaseAuthException e) {
            throw new com.smartstudy.shared.exception.BadRequestException("FIREBASE_ERROR", "Failed to delete Firebase account.");
        }

        userPreferenceRepository.deleteById(firebaseUid);
        userPreferenceRepository.flush();
        userRepository.delete(user);
        userRepository.flush();
        
        com.smartstudy.identity.dto.UserDeletedEvent event = new com.smartstudy.identity.dto.UserDeletedEvent(firebaseUid, System.currentTimeMillis());
        try {
            rabbitTemplate.convertAndSend(com.smartstudy.identity.config.RabbitMQConfig.EXCHANGE_NAME, com.smartstudy.identity.config.RabbitMQConfig.ROUTING_KEY, event);
        } catch (org.springframework.amqp.AmqpException e) {
            throw new com.smartstudy.shared.exception.BadRequestException("SERVICE_UNAVAILABLE", "Failed to publish user deletion event.");
        }
    }
}
