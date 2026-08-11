package com.smartstudy.planning.service;

import com.smartstudy.planning.model.Course;
import com.smartstudy.planning.model.Event;
import com.smartstudy.planning.model.EventType;
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
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TaskPriorityService {

    private static final Logger log = LoggerFactory.getLogger(TaskPriorityService.class);

    private static final int EXAM_HIGH_DAYS = 7;
    private static final int EXAM_MEDIUM_DAYS = 14;
    private static final int EVENT_SEARCH_WINDOW_DAYS = 14;

    private static final int SCORE_LOW = 33;
    private static final int SCORE_MEDIUM = 50;
    private static final int SCORE_HIGH_EXAM = 100;
    private static final int SCORE_HIGH_EVENT = 100;

    private final CourseRepository courseRepository;
    private final EventRepository eventRepository;

    public Priority determinePriority(String userId, UUID courseId, LocalDate scheduledDate) {
        LocalDate today = LocalDate.now();

        int daysToExam = Integer.MAX_VALUE;
        if (courseId != null) {
            daysToExam = computeDaysToExam(courseId, scheduledDate, today);
        }

        Event nearestEvent = findNearestEvent(userId, scheduledDate, today);

        int examScore = resolveExamProximityScore(daysToExam);
        int eventScore = resolveEventTypeScore(nearestEvent);

        int finalScore = Math.max(examScore, eventScore);
        Priority priority = scoreToPriority(finalScore);

        log.info("Auto-assigned priority {} for task on {} | score={} | examScore={} | eventScore={} | daysToExam={} | nearestEvent={}",
                priority, scheduledDate, finalScore, examScore, eventScore,
                daysToExam == Integer.MAX_VALUE ? "N/A" : daysToExam,
                nearestEvent != null ? nearestEvent.getEventType() : "NONE");
        return priority;
    }

    private int computeDaysToExam(UUID courseId, LocalDate scheduledDate, LocalDate today) {
        return courseRepository.findById(courseId)
                .map(Course::getExamDate)
                .map(instant -> instant.atZone(ZoneOffset.UTC).toLocalDate())
                .map(examDate -> (int) ChronoUnit.DAYS.between(today, examDate))
                .orElse(Integer.MAX_VALUE);
    }

    private Event findNearestEvent(String userId, LocalDate scheduledDate, LocalDate today) {
        Instant windowStart = scheduledDate.minusDays(3).atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant windowEnd = scheduledDate.plusDays(EVENT_SEARCH_WINDOW_DAYS)
                .atStartOfDay(ZoneOffset.UTC).toInstant();

        List<Event> events = eventRepository.findByUserIdAndStartDateBetween(userId, windowStart, windowEnd);

        if (events.isEmpty()) {
            return null;
        }

        return events.stream()
                .min(Comparator.comparing(e -> Math.abs(ChronoUnit.DAYS.between(scheduledDate,
                        e.getStartDate().atZone(ZoneOffset.UTC).toLocalDate()))))
                .orElse(null);
    }

    private int resolveExamProximityScore(int daysToExam) {
        if (daysToExam <= EXAM_HIGH_DAYS) {
            return SCORE_HIGH_EXAM;
        } else if (daysToExam <= EXAM_MEDIUM_DAYS) {
            return SCORE_MEDIUM;
        } else {
            return SCORE_LOW;
        }
    }

    private int resolveEventTypeScore(Event event) {
        if (event == null || event.getEventType() == null) {
            return SCORE_LOW;
        }

        Optional<EventType> eventType = EventType.fromWireValue(event.getEventType());
        if (eventType.isEmpty()) {
            return SCORE_LOW;
        }

        return switch (eventType.get()) {
            case EXAM -> SCORE_HIGH_EVENT;
            case ASSIGNMENT, QUIZ, PROJECT, MIDTERM -> SCORE_MEDIUM;
        };
    }

    private Priority scoreToPriority(int score) {
        if (score < 34) {
            return Priority.LOW;
        } else if (score < 67) {
            return Priority.MEDIUM;
        } else {
            return Priority.HIGH;
        }
    }
}
