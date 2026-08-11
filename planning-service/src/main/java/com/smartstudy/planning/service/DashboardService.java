package com.smartstudy.planning.service;

import com.smartstudy.planning.dto.response.DashboardResponse;
import com.smartstudy.planning.model.Course;
import com.smartstudy.planning.model.Task;
import com.smartstudy.planning.repository.CourseRepository;
import com.smartstudy.planning.repository.EventRepository;
import com.smartstudy.planning.repository.StudySessionRepository;
import com.smartstudy.planning.repository.TaskRepository;
import java.util.Comparator;
import com.smartstudy.shared.logging.LoggerFactory;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private static final Logger log = LoggerFactory.getLogger(DashboardService.class);
    private final TaskRepository taskRepository;
    private final CourseRepository courseRepository;
    private final EventRepository eventRepository;
    private final StudySessionRepository studySessionRepository;

    @Transactional(readOnly = true)
    public DashboardResponse getDashboard(String userId) {
        log.info("Fetching dashboard for userId: {}", userId);
        LocalDate today = LocalDate.now();
        List<Task> todayTasks = taskRepository.findByUserIdAndScheduledDateOrderByCreatedAtAsc(userId, today);
        long todayCompleted = todayTasks.stream().filter(Task::isCompleted).count();
        long todayMinutes = todayTasks.stream().mapToLong(Task::getDurationMinutes).sum();
        Course focusCourse = todayTasks.stream()
                .filter(task -> task.getCourseId() != null)
                .map(task -> courseRepository.findByIdAndUserId(task.getCourseId(), userId).orElse(null))
                .filter(course -> course != null)
                .findFirst()
                .orElse(null);

        DashboardResponse.TodayFocusResponse focus = new DashboardResponse.TodayFocusResponse(
                focusCourse != null ? focusCourse.getId() : null,
                focusCourse != null ? focusCourse.getName() : null,
                focusCourse != null ? focusCourse.getImageUrl() : null,
                formatDuration((int) todayMinutes),
                (int) todayMinutes);

        DashboardResponse.ProgressMetricsResponse metrics = new DashboardResponse.ProgressMetricsResponse(
                todayCompleted,
                todayTasks.size(),
                loggedHours(userId, today.minusDays(6), today),
                20,
                loggedHours(userId, today.withDayOfMonth(1), today),
                80);

        List<DashboardResponse.DashboardTaskResponse> taskResponses = todayTasks.stream()
                .map(task -> new DashboardResponse.DashboardTaskResponse(task.getId(), task.getTitle(),
                        task.getDurationMinutes(), task.getPriority(), task.getDescription(), task.getCoveredSections(),
                        task.isCompleted()))
                .toList();

        Instant now = Instant.now();
        Instant limit = now.plus(7, ChronoUnit.DAYS);

        // 1. Course-level deadlines (exams) within the next 7 days
        List<DashboardResponse.DeadlineResponse> courseDeadlines = courseRepository
                .findTop5ByUserIdAndExamDateAfterOrderByExamDateAsc(userId, now)
                .stream()
                .filter(course -> !course.getExamDate().isAfter(limit))
                .map(course -> {
                    long daysLeft = Duration.between(now, course.getExamDate()).toDays();
                    return new DashboardResponse.DeadlineResponse(course.getId(), "Exam", course.getName(),
                            "Exam", dueText(course.getExamDate()), course.getExamDate(), daysLeft);
                })
                .toList();

        // 2. Event-based deadlines (assignments, midterms, etc.) within the next 7 days
        List<DashboardResponse.DeadlineResponse> eventDeadlines = eventRepository
                .findByUserIdAndTaskIdIsNullAndStartDateBetween(userId, now, limit)
                .stream()
                .map(event -> {
                    String courseName = null;
                    if (event.getCourseId() != null) {
                        courseName = courseRepository.findByIdAndUserId(event.getCourseId(), userId)
                                .map(c -> c.getName()).orElse(null);
                    }
                    long daysLeft = Duration.between(now, event.getStartDate()).toDays();
                    return new DashboardResponse.DeadlineResponse(event.getId(), event.getTitle(), courseName,
                            event.getEventType(), dueText(event.getStartDate()), event.getStartDate(), daysLeft);
                })
                .toList();

        // Combine, sort by date, and limit to top 5 upcoming items
        List<DashboardResponse.DeadlineResponse> deadlines =
                Stream.concat(courseDeadlines.stream(), eventDeadlines.stream())
                        .sorted(Comparator.comparing(DashboardResponse.DeadlineResponse::dueDate))
                        .limit(5)
                        .toList();


        return new DashboardResponse(
                "Good day",
                focus,
                metrics,
                taskResponses,
                deadlines,
                new DashboardResponse.StreakResponse(todayCompleted > 0 ? 1 : 0),
                new DashboardResponse.AiSuggestionResponse("Keep your highest priority task first today."));
    }

    @Transactional(readOnly = true)
    public List<DashboardResponse.DeadlineResponse> getAllUpcomingDeadlines(String userId) {
        Instant now = Instant.now();
        Instant limit = now.plus(365, ChronoUnit.DAYS); // show all upcoming within a year by default

        List<DashboardResponse.DeadlineResponse> courseDeadlines = courseRepository
                .findTop5ByUserIdAndExamDateAfterOrderByExamDateAsc(userId, now)
                .stream()
                .map(course -> {
                    long daysLeft = Duration.between(now, course.getExamDate()).toDays();
                    return new DashboardResponse.DeadlineResponse(course.getId(), "Exam", course.getName(),
                            "Exam", dueText(course.getExamDate()), course.getExamDate(), daysLeft);
                })
                .toList();

        List<DashboardResponse.DeadlineResponse> eventDeadlines = eventRepository
                .findByUserIdAndTaskIdIsNullAndStartDateBetween(userId, now, limit)
                .stream()
                .map(event -> {
                    String courseName = null;
                    if (event.getCourseId() != null) {
                        courseName = courseRepository.findByIdAndUserId(event.getCourseId(), userId)
                                .map(c -> c.getName()).orElse(null);
                    }
                    long daysLeft = Duration.between(now, event.getStartDate()).toDays();
                    return new DashboardResponse.DeadlineResponse(event.getId(), event.getTitle(), courseName,
                            event.getEventType(), dueText(event.getStartDate()), event.getStartDate(), daysLeft);
                })
                .toList();

        return Stream.concat(courseDeadlines.stream(), eventDeadlines.stream())
                .sorted(Comparator.comparing(DashboardResponse.DeadlineResponse::dueDate))
                .toList();
    }

    private long loggedHours(String userId, LocalDate start, LocalDate end) {
        Instant from = start.atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant to = end.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        return studySessionRepository.findByUserIdAndEndedAtBetween(userId, from, to)
                .stream()
                .filter(session -> session.getDurationMinutesLogged() != null)
                .mapToLong(session -> session.getDurationMinutesLogged() / 60)
                .sum();
    }

    private String dueText(Instant dueDate) {
        long days = Duration.between(Instant.now(), dueDate).toDays();
        if (days <= 0) {
            return "Today";
        }
        if (days == 1) {
            return "Tomorrow";
        }
        return days + " Days left";
    }

    private String formatDuration(int minutes) {
        int hours = minutes / 60;
        int remainingMinutes = minutes % 60;
        if (hours == 0) {
            return remainingMinutes + "m";
        }
        if (remainingMinutes == 0) {
            return hours + "h";
        }
        return hours + "h " + remainingMinutes + "m";
    }
}
