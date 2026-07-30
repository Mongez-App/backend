package com.smartstudy.planning.service;

import com.smartstudy.planning.model.Course;
import com.smartstudy.planning.model.Event;
import com.smartstudy.planning.model.Priority;
import com.smartstudy.planning.repository.CourseRepository;
import com.smartstudy.planning.repository.EventRepository;
import com.smartstudy.shared.logging.LoggerFactory;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TaskPriorityService {

    private static final Logger log = LoggerFactory.getLogger(TaskPriorityService.class);

    private static final int EXAM_HIGH_DAYS = 7;
    private static final int EXAM_MEDIUM_DAYS = 14;
    private static final int EVENT_HIGH_DAYS = 3;
    private static final int EVENT_MEDIUM_DAYS = 7;
    private static final int EVENT_SEARCH_WINDOW_DAYS = 14;

    private final CourseRepository courseRepository;
    private final EventRepository eventRepository;

    public Priority determinePriority(String userId, UUID courseId, LocalDate scheduledDate) {
        LocalDate today = LocalDate.now();

        int daysToExam = Integer.MAX_VALUE;
        int daysToNearestEvent = Integer.MAX_VALUE;

        if (courseId != null) {
            daysToExam = computeDaysToExam(courseId, scheduledDate, today);
        }

        daysToNearestEvent = computeDaysToNearestEvent(userId, scheduledDate, today);

        Priority priority = resolvePriority(daysToExam, daysToNearestEvent);
        log.info("Auto-assigned priority {} for task on {} | daysToExam={} | daysToNearestEvent={}",
                priority, scheduledDate, daysToExam == Integer.MAX_VALUE ? "N/A" : daysToExam,
                daysToNearestEvent == Integer.MAX_VALUE ? "N/A" : daysToNearestEvent);
        return priority;
    }

    private int computeDaysToExam(UUID courseId, LocalDate scheduledDate, LocalDate today) {
        return courseRepository.findById(courseId)
                .map(Course::getExamDate)
                .map(instant -> instant.atZone(ZoneOffset.UTC).toLocalDate())
                .map(examDate -> (int) ChronoUnit.DAYS.between(today, examDate))
                .orElse(Integer.MAX_VALUE);
    }

    private int computeDaysToNearestEvent(String userId, LocalDate scheduledDate, LocalDate today) {
        Instant windowStart = scheduledDate.minusDays(3).atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant windowEnd = scheduledDate.plusDays(EVENT_SEARCH_WINDOW_DAYS)
                .atStartOfDay(ZoneOffset.UTC).toInstant();

        List<Event> events = eventRepository.findByUserIdAndStartDateBetween(userId, windowStart, windowEnd);

        if (events.isEmpty()) {
            return Integer.MAX_VALUE;
        }

        return events.stream()
                .map(event -> event.getStartDate().atZone(ZoneOffset.UTC).toLocalDate())
                .mapToInt(eventDate -> (int) Math.abs(ChronoUnit.DAYS.between(scheduledDate, eventDate)))
                .min()
                .orElse(Integer.MAX_VALUE);
    }

    private Priority resolvePriority(int daysToExam, int daysToNearestEvent) {
        boolean closeToExam = daysToExam <= EXAM_HIGH_DAYS;
        boolean nearExam = daysToExam <= EXAM_MEDIUM_DAYS;
        boolean closeToEvent = daysToNearestEvent <= EVENT_HIGH_DAYS;
        boolean nearEvent = daysToNearestEvent <= EVENT_MEDIUM_DAYS;

        if (closeToExam || closeToEvent) {
            return Priority.HIGH;
        }
        if (nearExam || nearEvent) {
            return Priority.MEDIUM;
        }
        return Priority.LOW;
    }
}
