package com.smartstudy.identity.service.impl;

import com.smartstudy.identity.client.PlanningServiceClient;
import com.smartstudy.identity.dto.request.SavePreferencesRequest;
import com.smartstudy.identity.dto.response.PreferencesResponse;
import com.smartstudy.identity.enums.WeekDay;
import com.smartstudy.identity.model.UserPreference;
import com.smartstudy.identity.repository.UserPreferenceRepository;
import com.smartstudy.identity.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserPreferenceRepository userPreferenceRepository;

    @Mock
    private PlanningServiceClient planningServiceClient;

    @Mock
    private RoadmapRescheduleTrigger roadmapRescheduleTrigger;

    @InjectMocks
    private UserServiceImpl userService;

    private final String uid = "test-uid-123";

    @BeforeEach
    void setUp() {
        when(userRepository.existsById(uid)).thenReturn(true);
    }

    @Test
    void testSavePreferences_NewUserPreference_SetsAvailableDaysAndDailyStudyHours() {
        when(userPreferenceRepository.findById(uid)).thenReturn(Optional.empty());

        SavePreferencesRequest request = new SavePreferencesRequest(4, List.of(WeekDay.Mon, WeekDay.Wed, WeekDay.Fri));

        PreferencesResponse response = userService.savePreferences(uid, request);

        assertNotNull(response);
        assertEquals("success", response.status());
        assertEquals(4, response.preferences().dailyStudyHours());
        assertEquals(3, response.preferences().availableDays().size());

        ArgumentCaptor<UserPreference> captor = ArgumentCaptor.forClass(UserPreference.class);
        verify(userPreferenceRepository).save(captor.capture());

        UserPreference savedPreference = captor.getValue();
        assertEquals(uid, savedPreference.getUserId());
        assertEquals(4, savedPreference.getDailyStudyHours());
        assertEquals(List.of(WeekDay.Mon, WeekDay.Wed, WeekDay.Fri), savedPreference.getAvailableDays());
    }

    @Test
    void testSavePreferences_ExistingUserPreference_UpdatesCollectionCorrectly() {
        List<WeekDay> existingDays = new ArrayList<>(List.of(WeekDay.Sat, WeekDay.Sun));
        UserPreference existingPreference = UserPreference.builder()
                .userId(uid)
                .dailyStudyHours(2)
                .availableDays(existingDays)
                .build();

        when(userPreferenceRepository.findById(uid)).thenReturn(Optional.of(existingPreference));

        SavePreferencesRequest request = new SavePreferencesRequest(6, List.of(WeekDay.Mon, WeekDay.Tue));

        PreferencesResponse response = userService.savePreferences(uid, request);

        assertNotNull(response);
        assertEquals(6, response.preferences().dailyStudyHours());
        assertEquals(List.of(WeekDay.Mon, WeekDay.Tue), response.preferences().availableDays());

        verify(userPreferenceRepository).save(existingPreference);
    }
}
