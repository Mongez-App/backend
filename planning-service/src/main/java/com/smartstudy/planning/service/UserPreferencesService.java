package com.smartstudy.planning.service;

import com.smartstudy.planning.client.IdentityServiceClient;
import com.smartstudy.planning.dto.response.UserPreferencesData;
import com.smartstudy.shared.logging.LoggerFactory;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Single source of truth for a user's study preferences inside planning-service.
 * <p>
 * Preferences are owned by identity-service (daily study hours + available days,
 * set at onboarding and editable from the profile). Everything that schedules
 * work or reports study goals must read them from here rather than trusting
 * request headers, which no client actually sends.
 */
@Service
@RequiredArgsConstructor
public class UserPreferencesService {

    /** Used only when the user has never saved preferences, so scheduling still has capacity. */
    public static final int DEFAULT_DAILY_STUDY_MINUTES = 60;
    public static final String DEFAULT_PREFERRED_DAYS = "MON,TUE,WED,THU,FRI,SAT,SUN";

    private static final Logger log = LoggerFactory.getLogger(UserPreferencesService.class);
    private static final int MINUTES_PER_HOUR = 60;

    private final IdentityServiceClient identityServiceClient;

    public StudyPreferences resolve(String userId) {
        return resolve(userId, DEFAULT_DAILY_STUDY_MINUTES, DEFAULT_PREFERRED_DAYS);
    }

    /**
     * Loads the user's saved preferences. The fallbacks apply only to the values
     * handed to the scheduler, and only when nothing has been saved yet; the
     * goal figures always reflect what the user actually chose.
     */
    public StudyPreferences resolve(String userId, int fallbackDailyStudyMinutes, String fallbackPreferredDays) {
        UserPreferencesData saved = fetch(userId);

        int savedHours = saved != null && saved.dailyStudyHours() != null ? saved.dailyStudyHours() : 0;
        Set<DayOfWeek> savedDays = saved != null ? parseDays(saved.availableDays()) : EnumSet.noneOf(DayOfWeek.class);

        int schedulingMinutes = savedHours > 0
                ? savedHours * MINUTES_PER_HOUR
                : fallbackDailyStudyMinutes;
        Set<DayOfWeek> schedulingDays = !savedDays.isEmpty()
                ? savedDays
                : parseCsv(fallbackPreferredDays);
        if (schedulingDays.isEmpty()) {
            schedulingDays = EnumSet.allOf(DayOfWeek.class);
        }

        log.debug("Resolved preferences for user {}: {} min/day over {} (saved: {}h, {} days)",
                userId, schedulingMinutes, schedulingDays, savedHours, savedDays.size());

        return new StudyPreferences(schedulingMinutes, schedulingDays, savedHours, savedDays);
    }

    private UserPreferencesData fetch(String userId) {
        try {
            return identityServiceClient.getPreferences(userId);
        } catch (Exception ex) {
            log.warn("Failed to load preferences for user {}: {}", userId, ex.getMessage());
            return null;
        }
    }

    private static Set<DayOfWeek> parseDays(List<String> days) {
        EnumSet<DayOfWeek> parsed = EnumSet.noneOf(DayOfWeek.class);
        if (days == null) {
            return parsed;
        }
        for (String day : days) {
            DayOfWeek dayOfWeek = toDayOfWeek(day);
            if (dayOfWeek != null) {
                parsed.add(dayOfWeek);
            }
        }
        return parsed;
    }

    /** Parses the comma-separated form used by request headers and the scheduler. */
    private static Set<DayOfWeek> parseCsv(String days) {
        if (days == null || days.isBlank()) {
            return EnumSet.noneOf(DayOfWeek.class);
        }
        return parseDays(List.of(days.split(",")));
    }

    /** Accepts the identity short form ("Mon"), the full name, or any casing. */
    private static DayOfWeek toDayOfWeek(String day) {
        if (day == null || day.isBlank()) {
            return null;
        }
        String normalized = day.trim().toUpperCase();
        for (DayOfWeek candidate : DayOfWeek.values()) {
            if (candidate.name().startsWith(normalized)) {
                return candidate;
            }
        }
        log.warn("Ignoring unrecognised preferred day: {}", day);
        return null;
    }

    /** Renders days in the comma-separated short form the scheduler's parser expects. */
    private static String toCsv(Set<DayOfWeek> days) {
        return days.stream()
                .sorted()
                .map(day -> day.name().substring(0, 3))
                .collect(Collectors.joining(","));
    }

    /**
     * @param dailyStudyMinutes daily budget to schedule against (fallback applied)
     * @param schedulingDays    days to schedule on (fallback applied), never empty
     * @param savedDailyHours   hours the user saved, 0 when unset
     * @param savedDays         days the user saved, empty when unset
     */
    public record StudyPreferences(
            int dailyStudyMinutes,
            Set<DayOfWeek> schedulingDays,
            int savedDailyHours,
            Set<DayOfWeek> savedDays
    ) {

        /** Scheduling days in the comma-separated form the scheduler's parser expects. */
        public String preferredDays() {
            return toCsv(schedulingDays);
        }

        /** Target hours for one full week: daily hours on each chosen day. */
        public long weeklyGoalHours() {
            return (long) savedDailyHours * savedDays.size();
        }

        /** Target hours for {@code month}: daily hours on every chosen weekday it contains. */
        public long monthlyGoalHours(YearMonth month) {
            if (savedDailyHours == 0 || savedDays.isEmpty()) {
                return 0;
            }
            long matchingDays = 0;
            for (LocalDate date = month.atDay(1); !date.isAfter(month.atEndOfMonth()); date = date.plusDays(1)) {
                if (savedDays.contains(date.getDayOfWeek())) {
                    matchingDays++;
                }
            }
            return savedDailyHours * matchingDays;
        }
    }
}
