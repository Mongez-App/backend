package com.smartstudy.planning.service;

import com.smartstudy.planning.ai.model.AgentCheckResult;
import com.smartstudy.planning.dto.response.AlertResponse;
import com.smartstudy.planning.dto.response.RoadmapResponse;
import com.smartstudy.planning.model.Course;
import com.smartstudy.planning.model.Event;
import com.smartstudy.planning.model.Task;
import com.smartstudy.planning.repository.CourseRepository;
import com.smartstudy.planning.repository.EventRepository;
import com.smartstudy.planning.repository.TaskRepository;
import com.smartstudy.shared.logging.LoggerFactory;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RoadmapService {

    private static final Logger log = LoggerFactory.getLogger(RoadmapService.class);
    private final TaskRepository taskRepository;
    private final CourseRepository courseRepository;
    private final EventRepository eventRepository;
    private final StudyPlannerAgent studyPlannerAgent;

    @Transactional(readOnly = true)
    public RoadmapResponse getWeeklyRoadmap(String userId, LocalDate startDate) {
        log.info("Fetching weekly roadmap for userId: {} | startDate: {}", userId, startDate);
        return buildWeeklyResponse(userId, weekStart(startDate), null);
    }

    @Transactional
    public RoadmapResponse reschedule(String userId, int dailyStudyMinutes, String preferredDays) {
        log.info("Rescheduling roadmap for userId: {}", userId);
        AgentCheckResult result = studyPlannerAgent.checkAndRescheduleRoadmap(userId, dailyStudyMinutes, preferredDays);
        LocalDate startDate = weekStart(LocalDate.now().plusDays(1));
        return buildWeeklyResponse(userId, startDate, result.alert());
    }

    private RoadmapResponse buildWeeklyResponse(String userId, LocalDate startDate, AlertResponse alert) {
        // Fetch all tasks for this user from startDate onwards (up to 12 weeks ahead)
        LocalDate maxEndDate = startDate.plusWeeks(12).minusDays(1);
        List<Task> allTasks = taskRepository
                .findByUserIdAndScheduledDateBetweenOrderByScheduledDateAscCreatedAtAsc(userId, startDate, maxEndDate);

        if (allTasks.isEmpty()) {
            // Return empty response with a single week
            LocalDate endDate = startDate.plusDays(6);
            RoadmapResponse.WeekResponse emptyWeek = new RoadmapResponse.WeekResponse(
                    1, startDate, endDate, List.of());
            return new RoadmapResponse(startDate, List.of(emptyWeek), alert);
        }

        // Determine the actual date range from tasks
        LocalDate lastTaskDate = allTasks.stream()
                .map(Task::getScheduledDate)
                .max(Comparator.naturalOrder())
                .orElse(startDate.plusDays(6));
        LocalDate rangeEndDate = weekEnd(lastTaskDate);

        // Fetch courses for name lookups
        Map<UUID, Course> courses = courseRepository.findByUserIdOrderByCreatedAtAsc(userId)
                .stream()
                .collect(Collectors.toMap(Course::getId, Function.identity()));

        // Fetch only user-created events (taskId IS NULL) spanning the full range
        // AI-generated events (taskId != null) are internal scheduling artifacts and not shown
        List<Event> userEvents = eventRepository.findByUserIdAndTaskIdIsNullAndStartDateBetween(
                userId,
                startDate.atStartOfDay().toInstant(ZoneOffset.UTC),
                rangeEndDate.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC)
        );

        // Group tasks by week number
        long totalWeeks = ChronoUnit.WEEKS.between(startDate, rangeEndDate) + 1;
        List<RoadmapResponse.WeekResponse> weeks = new ArrayList<>();

        for (int w = 0; w < totalWeeks; w++) {
            LocalDate weekStartDate = startDate.plusWeeks(w);
            LocalDate weekEndDate = weekStartDate.plusDays(6);
            int weekNumber = w + 1;

            List<RoadmapResponse.StudyBlockResponse> studyBlocks = allTasks.stream()
                    .filter(t -> !t.getScheduledDate().isBefore(weekStartDate)
                            && !t.getScheduledDate().isAfter(weekEndDate))
                    .sorted(Comparator.comparing(Task::getScheduledDate)
                            .thenComparing(Task::getCreatedAt))
                    .map(task -> toStudyBlockResponse(task, courses, userEvents))
                    .toList();

            weeks.add(new RoadmapResponse.WeekResponse(weekNumber, weekStartDate, weekEndDate, studyBlocks));
        }

        return new RoadmapResponse(startDate, weeks, alert);
    }

    private RoadmapResponse.StudyBlockResponse toStudyBlockResponse(
            Task task, Map<UUID, Course> courses, List<Event> userEvents) {

        Course course = task.getCourseId() != null ? courses.get(task.getCourseId()) : null;
        String courseName = course != null ? course.getName() : "Unknown Course";

        // Find a user-created event matching this task's course and scheduled date
        RoadmapResponse.RoadmapEventResponse eventResponse = null;
        if (task.getCourseId() != null) {
            Event matchedEvent = userEvents.stream()
                    .filter(e -> task.getCourseId().equals(e.getCourseId()))
                    .filter(e -> e.getStartDate().atZone(ZoneOffset.UTC)
                            .toLocalDate().equals(task.getScheduledDate()))
                    .findFirst()
                    .orElse(null);

            if (matchedEvent != null) {
                String eventCourseName = matchedEvent.getCourseId() != null
                        ? courses.getOrDefault(matchedEvent.getCourseId(), course) != null
                            ? courses.getOrDefault(matchedEvent.getCourseId(), course).getName()
                            : courseName
                        : courseName;
                eventResponse = new RoadmapResponse.RoadmapEventResponse(
                        matchedEvent.getId().toString(),
                        matchedEvent.getCourseId(),
                        eventCourseName,
                        matchedEvent.getTitle(),
                        matchedEvent.getEventType(),
                        matchedEvent.getStartDate().toString()
                );
            }
        }

        return new RoadmapResponse.StudyBlockResponse(
                task.getId(),
                task.getCourseId(),
                courseName,
                task.getTitle(),
                task.getDurationMinutes(),
                task.isCompleted(),
                eventResponse
        );
    }

    private LocalDate weekStart(LocalDate date) {
        return date.with(DayOfWeek.MONDAY);
    }

    private LocalDate weekEnd(LocalDate date) {
        return date.with(DayOfWeek.SUNDAY);
    }
}
