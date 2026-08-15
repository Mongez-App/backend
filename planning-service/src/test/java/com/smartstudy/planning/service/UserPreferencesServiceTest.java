package com.smartstudy.planning.service;

import com.smartstudy.planning.client.IdentityServiceClient;
import com.smartstudy.planning.dto.response.UserPreferencesData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.DayOfWeek;
import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserPreferencesServiceTest {

    @Mock
    private IdentityServiceClient identityServiceClient;

    @InjectMocks
    private UserPreferencesService userPreferencesService;

    private final String userId = "test-user";

    // --- Scheduling inputs ---

    @Test
    void testSavedDaysDriveScheduling_notTheHeaderDefault() {
        when(identityServiceClient.getPreferences(userId))
                .thenReturn(new UserPreferencesData(3, List.of("Sun", "Tue", "Thu")));

        UserPreferencesService.StudyPreferences preferences = userPreferencesService.resolve(
                userId, 60, UserPreferencesService.DEFAULT_PREFERRED_DAYS);

        assertEquals(180, preferences.dailyStudyMinutes());
        assertEquals("TUE,THU,SUN", preferences.preferredDays());
        assertEquals(
                java.util.Set.of(DayOfWeek.SUNDAY, DayOfWeek.TUESDAY, DayOfWeek.THURSDAY),
                preferences.schedulingDays());
    }

    @Test
    void testEditedPreferencesAreReflectedImmediately() {
        when(identityServiceClient.getPreferences(userId))
                .thenReturn(new UserPreferencesData(1, List.of("Mon", "Wed", "Fri")));

        UserPreferencesService.StudyPreferences preferences = userPreferencesService.resolve(userId);

        assertEquals(60, preferences.dailyStudyMinutes());
        assertEquals("MON,WED,FRI", preferences.preferredDays());
    }

    @Test
    void testFallsBackToSuppliedDefaults_whenPreferencesNeverSaved() {
        when(identityServiceClient.getPreferences(userId))
                .thenReturn(new UserPreferencesData(0, List.of()));

        UserPreferencesService.StudyPreferences preferences = userPreferencesService.resolve(
                userId, 90, "MON,WED");

        assertEquals(90, preferences.dailyStudyMinutes());
        assertEquals("MON,WED", preferences.preferredDays());
    }

    @Test
    void testFallsBackToSuppliedDefaults_whenIdentityServiceUnavailable() {
        when(identityServiceClient.getPreferences(userId)).thenReturn(null);

        UserPreferencesService.StudyPreferences preferences = userPreferencesService.resolve(userId);

        assertEquals(UserPreferencesService.DEFAULT_DAILY_STUDY_MINUTES, preferences.dailyStudyMinutes());
        assertEquals(7, preferences.schedulingDays().size());
    }

    @Test
    void testUnrecognisedDaysAreIgnored() {
        when(identityServiceClient.getPreferences(userId))
                .thenReturn(new UserPreferencesData(2, List.of("Mon", "Funday", "Fri")));

        UserPreferencesService.StudyPreferences preferences = userPreferencesService.resolve(userId);

        assertEquals("MON,FRI", preferences.preferredDays());
    }

    // --- Dashboard goals ---

    @Test
    void testWeeklyGoalIsDailyHoursTimesChosenDays() {
        when(identityServiceClient.getPreferences(userId))
                .thenReturn(new UserPreferencesData(3, List.of("Sun", "Tue", "Thu")));

        assertEquals(9, userPreferencesService.resolve(userId).weeklyGoalHours());
    }

    @Test
    void testWeeklyGoalDiffersPerUser() {
        when(identityServiceClient.getPreferences("light-user"))
                .thenReturn(new UserPreferencesData(1, List.of("Sat")));
        when(identityServiceClient.getPreferences("heavy-user"))
                .thenReturn(new UserPreferencesData(4, List.of("Mon", "Tue", "Wed", "Thu", "Fri")));

        assertEquals(1, userPreferencesService.resolve("light-user").weeklyGoalHours());
        assertEquals(20, userPreferencesService.resolve("heavy-user").weeklyGoalHours());
    }

    @Test
    void testMonthlyGoalCountsMatchingDaysInThatMonth() {
        when(identityServiceClient.getPreferences(userId))
                .thenReturn(new UserPreferencesData(2, List.of("Mon")));

        // September 2026 starts on a Tuesday and contains four Mondays (7, 14, 21, 28).
        assertEquals(8, userPreferencesService.resolve(userId).monthlyGoalHours(YearMonth.of(2026, 9)));
    }

    @Test
    void testGoalsAreZero_whenPreferencesNeverSaved() {
        when(identityServiceClient.getPreferences(userId))
                .thenReturn(new UserPreferencesData(0, List.of()));

        UserPreferencesService.StudyPreferences preferences = userPreferencesService.resolve(userId);

        assertEquals(0, preferences.weeklyGoalHours());
        assertEquals(0, preferences.monthlyGoalHours(YearMonth.of(2026, 9)));
    }
}
