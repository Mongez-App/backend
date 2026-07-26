package com.smartstudy.planning.service;

import com.smartstudy.planning.dto.request.RescheduleRoadmapRequest;
import com.smartstudy.planning.dto.response.AlertResponse;
import com.smartstudy.planning.dto.response.RoadmapResponse;
import com.smartstudy.planning.model.Course;
import com.smartstudy.planning.model.Task;
import com.smartstudy.planning.repository.CourseRepository;
import com.smartstudy.planning.repository.TaskRepository;
import com.smartstudy.shared.logging.LoggerFactory;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
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

    @Transactional(readOnly = true)
    public RoadmapResponse getWeeklyRoadmap(String userId, LocalDate startDate) {
        log.info("Fetching weekly roadmap for userId: {} | startDate: {}", userId, startDate);
        return buildWeeklyResponse(userId, weekStart(startDate), null);
    }

    @Transactional
    public RoadmapResponse reschedule(String userId, RescheduleRoadmapRequest request) {
        log.info("Rescheduling tasks {} for userId: {}", request.taskIds(), userId);
        List<Task> tasks = taskRepository.findByIdInAndUserId(request.taskIds(), userId);
        LocalDate targetDate = LocalDate.now().plusDays(1);
        tasks.forEach(task -> task.setScheduledDate(targetDate));
        LocalDate startDate = weekStart(targetDate);
        String message = tasks.size() + " task" + (tasks.size() == 1 ? " was" : "s were")
                + " moved to " + targetDate.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH) + ".";
        return buildWeeklyResponse(userId, startDate, new AlertResponse(message));
    }

    private RoadmapResponse buildWeeklyResponse(String userId, LocalDate startDate, AlertResponse alert) {
        LocalDate endDate = startDate.plusDays(6);
        List<Task> tasks = taskRepository
                .findByUserIdAndScheduledDateBetweenOrderByScheduledDateAscCreatedAtAsc(userId, startDate, endDate);
        Map<UUID, Course> courses = courseRepository.findByUserIdOrderByCreatedAtAsc(userId)
                .stream()
                .collect(Collectors.toMap(Course::getId, Function.identity()));
        Map<LocalDate, List<Task>> byDay = tasks.stream()
                .collect(Collectors.groupingBy(Task::getScheduledDate));

        List<RoadmapResponse.DayResponse> days = byDay.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> toDayResponse(entry.getKey(), entry.getValue(), courses))
                .toList();

        RoadmapResponse.WeekResponse week = new RoadmapResponse.WeekResponse(1, startDate, endDate, days);
        return new RoadmapResponse(startDate, List.of(week), alert);
    }

    private RoadmapResponse.DayResponse toDayResponse(LocalDate date, List<Task> tasks, Map<UUID, Course> courses) {
        List<RoadmapResponse.RoadmapTaskResponse> taskResponses = tasks.stream()
                .sorted(Comparator.comparing(Task::getCreatedAt))
                .map(task -> {
                    Course course = courses.get(task.getCourseId());
                    String courseName = course != null ? course.getName() : "Unknown Course";
                    return new RoadmapResponse.RoadmapTaskResponse(task.getId(), task.getCourseId(), courseName, task.getTitle(),
                            task.getDurationMinutes(), task.isCompleted());
                })
                .toList();
        return new RoadmapResponse.DayResponse(date,
                date.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH), taskResponses);
    }

    private LocalDate weekStart(LocalDate date) {
        return date.with(DayOfWeek.MONDAY);
    }
}
