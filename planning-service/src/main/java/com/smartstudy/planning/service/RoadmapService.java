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

            // 1. Get tasks for this week
            List<Task> weekTasks = allTasks.stream()
                    .filter(t -> !t.getScheduledDate().isBefore(weekStartDate)
                            && !t.getScheduledDate().isAfter(weekEndDate))
                    .sorted(Comparator.comparing(Task::getScheduledDate)
                            .thenComparing(Task::getCreatedAt))
                    .toList();
            
            // 2. Get events for this week
            List<Event> weekEvents = userEvents.stream()
                    .filter(e -> {
                        LocalDate eventDate = e.getStartDate().atZone(ZoneOffset.UTC).toLocalDate();
                        return !eventDate.isBefore(weekStartDate) && !eventDate.isAfter(weekEndDate);
                    })
                    .toList();

            List<RoadmapResponse.StudyBlockResponse> studyBlocks = new ArrayList<>();
            List<Event> matchedEvents = new ArrayList<>();

            // 3. Create study blocks from tasks, attaching events if they match
            for (Task task : weekTasks) {
                Event matchedEvent = null;
                if (task.getCourseId() != null) {
                    matchedEvent = weekEvents.stream()
                            .filter(e -> !matchedEvents.contains(e)) // Don't match the same event twice
                            .filter(e -> task.getCourseId().equals(e.getCourseId()))
                            .filter(e -> e.getStartDate().atZone(ZoneOffset.UTC).toLocalDate().equals(task.getScheduledDate()))
                            .findFirst()
                            .orElse(null);
                }
                
                if (matchedEvent != null) {
                    matchedEvents.add(matchedEvent);
                }
                
                studyBlocks.add(toStudyBlockResponse(task, courses, matchedEvent));
            }

            // 4. Create standalone study blocks for any unmatched events
            for (Event event : weekEvents) {
                if (!matchedEvents.contains(event)) {
                    studyBlocks.add(toStandaloneEventBlock(event, courses));
                }
            }

            weeks.add(new RoadmapResponse.WeekResponse(weekNumber, weekStartDate, weekEndDate, studyBlocks));
        }

        return new RoadmapResponse(startDate, weeks, alert);
    }

    private RoadmapResponse.StudyBlockResponse toStudyBlockResponse(
            Task task, Map<UUID, Course> courses, Event matchedEvent) {

        Course course = task.getCourseId() != null ? courses.get(task.getCourseId()) : null;
        String courseName = course != null ? course.getName() : "Unknown Course";

        RoadmapResponse.RoadmapEventResponse eventResponse = null;
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

    private RoadmapResponse.StudyBlockResponse toStandaloneEventBlock(
            Event event, Map<UUID, Course> courses) {
            
        Course course = event.getCourseId() != null ? courses.get(event.getCourseId()) : null;
        String courseName = course != null ? course.getName() : "Unknown Course";

        RoadmapResponse.RoadmapEventResponse eventResponse = new RoadmapResponse.RoadmapEventResponse(
                event.getId().toString(),
                event.getCourseId(),
                courseName,
                event.getTitle(),
                event.getEventType(),
                event.getStartDate().toString()
        );

        return new RoadmapResponse.StudyBlockResponse(
                event.getId(), // Use event ID as block ID for standalone events
                event.getCourseId(),
                courseName,
                event.getTitle(), // Use event title as topic
                90, // 90 minutes duration for standalone events
                false, 
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
