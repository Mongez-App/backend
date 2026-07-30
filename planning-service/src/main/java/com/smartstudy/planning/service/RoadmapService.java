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

import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

        // Fetch only user-created events (taskId IS NULL) over the same window
        // AI-generated events (taskId != null) are internal scheduling artifacts and not shown
        List<Event> userEvents = eventRepository.findByUserIdAndTaskIdIsNullAndStartDateBetween(
                userId,
                startDate.atStartOfDay().toInstant(ZoneOffset.UTC),
                maxEndDate.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC)
        );

        if (allTasks.isEmpty() && userEvents.isEmpty()) {
            // Return empty response with a single week
            LocalDate endDate = startDate.plusDays(6);
            RoadmapResponse.WeekResponse emptyWeek = new RoadmapResponse.WeekResponse(
                    1, startDate, endDate, List.of());
            return new RoadmapResponse(startDate, List.of(emptyWeek), alert);
        }

        // Determine the actual date range from whichever runs later: tasks or events
        LocalDate lastDate = startDate;
        for (Task task : allTasks) {
            if (task.getScheduledDate().isAfter(lastDate)) {
                lastDate = task.getScheduledDate();
            }
        }
        for (Event event : userEvents) {
            LocalDate eventDate = toLocalDate(event);
            if (eventDate.isAfter(lastDate)) {
                lastDate = eventDate;
            }
        }
        LocalDate rangeEndDate = weekEnd(lastDate);

        // Fetch courses for name lookups
        Map<UUID, Course> courses = courseRepository.findByUserIdOrderByCreatedAtAsc(userId)
                .stream()
                .collect(Collectors.toMap(Course::getId, Function.identity()));

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
                        LocalDate eventDate = toLocalDate(e);
                        return !eventDate.isBefore(weekStartDate) && !eventDate.isAfter(weekEndDate);
                    })
                    .sorted(Comparator.comparing(Event::getStartDate))
                    .toList();

            // 3. One study block per course per week, holding that course's tasks and events.
            //    Course order follows first task occurrence, then course-only events.
            Map<UUID, List<Task>> tasksByCourse = new LinkedHashMap<>();
            for (Task task : weekTasks) {
                tasksByCourse.computeIfAbsent(task.getCourseId(), k -> new ArrayList<>()).add(task);
            }
            Map<UUID, List<Event>> eventsByCourse = new LinkedHashMap<>();
            for (Event event : weekEvents) {
                eventsByCourse.computeIfAbsent(event.getCourseId(), k -> new ArrayList<>()).add(event);
            }

            Set<UUID> courseOrder = new LinkedHashSet<>(tasksByCourse.keySet());
            courseOrder.addAll(eventsByCourse.keySet());

            List<RoadmapResponse.StudyBlockResponse> studyBlocks = new ArrayList<>();
            for (UUID courseId : courseOrder) {
                studyBlocks.add(toStudyBlockResponse(
                        courseId,
                        weekStartDate,
                        tasksByCourse.getOrDefault(courseId, List.of()),
                        eventsByCourse.getOrDefault(courseId, List.of()),
                        courses));
            }

            weeks.add(new RoadmapResponse.WeekResponse(weekNumber, weekStartDate, weekEndDate, studyBlocks));
        }

        return new RoadmapResponse(startDate, weeks, alert);
    }

    private RoadmapResponse.StudyBlockResponse toStudyBlockResponse(
            UUID courseId,
            LocalDate weekStartDate,
            List<Task> blockTasks,
            List<Event> blockEvents,
            Map<UUID, Course> courses) {

        Course course = courseId != null ? courses.get(courseId) : null;
        String courseName = course != null ? course.getName() : "Unknown Course";

        List<RoadmapResponse.RoadmapTaskResponse> tasks = blockTasks.stream()
                .map(task -> new RoadmapResponse.RoadmapTaskResponse(
                        task.getTitle(),
                        task.getDurationMinutes(),
                        task.getScheduledDate()))
                .toList();

        List<RoadmapResponse.RoadmapEventResponse> events = blockEvents.stream()
                .map(event -> new RoadmapResponse.RoadmapEventResponse(
                        event.getId().toString(),
                        event.getCourseId(),
                        courseName,
                        event.getTitle(),
                        event.getEventType(),
                        event.getStartDate().toString()))
                .toList();

        // A block is complete once every task in it is done AND all related events' dates have passed.
        boolean tasksCompleted = !blockTasks.isEmpty() && blockTasks.stream().allMatch(Task::isCompleted);
        LocalDate today = LocalDate.now();
        boolean eventsPassed = blockEvents.stream().allMatch(e -> !toLocalDate(e).isAfter(today));
        boolean completed = tasksCompleted && eventsPassed;

        return new RoadmapResponse.StudyBlockResponse(
                blockId(courseId, weekStartDate),
                courseId,
                courseName,
                tasks,
                completed,
                events
        );
    }

    /** Stable, derived block id: same course in the same week always yields the same value. */
    private UUID blockId(UUID courseId, LocalDate weekStartDate) {
        return UUID.nameUUIDFromBytes(
                ("block:" + courseId + ":" + weekStartDate).getBytes(StandardCharsets.UTF_8));
    }

    private LocalDate toLocalDate(Event event) {
        return event.getStartDate().atZone(ZoneOffset.UTC).toLocalDate();
    }

    private LocalDate weekStart(LocalDate date) {
        return date.with(DayOfWeek.MONDAY);
    }

    private LocalDate weekEnd(LocalDate date) {
        return date.with(DayOfWeek.SUNDAY);
    }
}
