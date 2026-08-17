package com.smartstudy.planning.ai.tool;

import com.smartstudy.planning.model.Course;
import com.smartstudy.planning.model.Task;
import com.smartstudy.planning.repository.CourseRepository;
import com.smartstudy.planning.repository.TaskRepository;
import com.smartstudy.shared.logging.LoggerFactory;
import com.smartstudy.planning.ai.model.AvailableSlot;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class CalendarQuerierTool {

    private static final Logger log = LoggerFactory.getLogger(CalendarQuerierTool.class);
    private static final int MIN_SLOT_MINUTES = 20;
    private final CourseRepository courseRepository;
    private final TaskRepository taskRepository;

    @Tool(name = "query_available_slots", description = "Query available study slots between now and the exam date based on preferred study days and daily time budget. Input: userId, courseId, dailyStudyMinutes, preferredDays (comma-separated MON,WED,FRI). Returns list of AvailableSlot records.")
    public List<AvailableSlot> query(String userId, String courseId, int dailyStudyMinutes, String preferredDays) {
        return query(userId, courseId, dailyStudyMinutes, preferredDays, null);
    }

    public List<AvailableSlot> query(String userId, String courseId, int dailyStudyMinutes, String preferredDays,
                                     LocalDate endDateExclusive) {
        return query(userId, courseId, dailyStudyMinutes, preferredDays, endDateExclusive, Set.of());
    }

    public List<AvailableSlot> query(String userId, String courseId, int dailyStudyMinutes, String preferredDays,
                                     LocalDate endDateExclusive, Collection<UUID> ignoredTaskIds) {
        java.util.UUID parsedCourseId = java.util.UUID.fromString(courseId);
        Set<DayOfWeek> preferred = parsePreferredDays(preferredDays);
        Set<UUID> ignoredIds = ignoredTaskIds != null ? new HashSet<>(ignoredTaskIds) : Set.of();
        LocalDate today = LocalDate.now();
        LocalDate startDate = LocalTime.now().isBefore(LocalTime.of(14, 0)) ? today : today.plusDays(1);
        Course course = courseRepository.findByIdAndUserId(parsedCourseId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "COURSE_NOT_FOUND"));
        LocalDate examDate = course.getExamDate() != null
                ? course.getExamDate().atZone(java.time.ZoneOffset.UTC).toLocalDate()
                : LocalDate.now().plusYears(1);
        LocalDate scheduleEndDate = endDateExclusive != null && endDateExclusive.isBefore(examDate)
                ? endDateExclusive
                : examDate;

        List<AvailableSlot> slots = new ArrayList<>();
        for (LocalDate date = startDate; date.isBefore(scheduleEndDate); date = date.plusDays(1)) {
            if (!preferred.contains(date.getDayOfWeek())) {
                continue;
            }

            int alreadyBooked = taskRepository.findByUserIdAndCourseIdAndScheduledDateAndCompletedFalseAndMissedFalse(userId, parsedCourseId, date)
                    .stream()
                    .filter(task -> task.getId() == null || !ignoredIds.contains(task.getId()))
                    .mapToInt(Task::getDurationMinutes)
                    .sum();

            int remaining = dailyStudyMinutes - alreadyBooked;
            if (remaining >= MIN_SLOT_MINUTES) {
                slots.add(new AvailableSlot(date, remaining));
                log.debug("Slot {} : {} min available ({} already booked)", date, remaining, alreadyBooked);
            }
        }
        return slots;
    }

    private Set<DayOfWeek> parsePreferredDays(String preferredDays) {
        EnumSet<DayOfWeek> set = EnumSet.noneOf(DayOfWeek.class);
        if (preferredDays == null || preferredDays.isBlank()) {
            return set;
        }
        for (String part : preferredDays.split(",")) {
            String trimmed = part.trim().toUpperCase();
            if (!trimmed.isEmpty()) {
                String normalized = switch (trimmed) {
                    case "MON" -> "MONDAY";
                    case "TUE" -> "TUESDAY";
                    case "WED" -> "WEDNESDAY";
                    case "THU" -> "THURSDAY";
                    case "FRI" -> "FRIDAY";
                    case "SAT" -> "SATURDAY";
                    case "SUN" -> "SUNDAY";
                    default -> trimmed;
                };
                set.add(DayOfWeek.valueOf(normalized));
            }
        }
        return set;
    }
}
