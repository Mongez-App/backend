package com.smartstudy.planning.service;

import com.smartstudy.planning.dto.response.CourseTeamResponse;
import com.smartstudy.planning.dto.response.TeamEventSummary;
import com.smartstudy.planning.dto.response.TeamStatsResponse;
import com.smartstudy.planning.model.Course;
import com.smartstudy.planning.model.Event;
import com.smartstudy.planning.repository.CourseRepository;
import com.smartstudy.planning.repository.EventRepository;
import com.smartstudy.planning.repository.TaskRepository;
import com.smartstudy.shared.logging.LoggerFactory;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TeamCourseServiceImpl implements TeamCourseService {

    private static final Logger log = LoggerFactory.getLogger(TeamCourseServiceImpl.class);
    private final CourseRepository courseRepository;
    private final TaskRepository taskRepository;
    private final EventRepository eventRepository;

    @Override
    @Transactional(readOnly = true)
    public List<CourseTeamResponse> getTeamCourses(String teamId, String userId) {
        log.info("Fetching team courses for teamId: {} | userId: {}", teamId, userId);
        List<Course> courses = courseRepository.findByTeamIdAndUserId(teamId, userId);

        return courses.stream().map(course -> {
            double completion = calculateCompletionPercentage(userId, course.getId());
            List<TeamEventSummary> events = getUpcomingEventTypes(userId, course.getId());

            return new CourseTeamResponse(
                    course.getId(),
                    course.getTeamId(),
                    course.getUserId(),
                    course.getName(),
                    course.getCourseCode(),
                    course.getStartDate(),
                    course.getExamDate(),
                    course.getImageUrl(),
                    completion
            );
        }).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public TeamStatsResponse getTeamStats(String teamId, String userId) {
        log.info("Fetching team stats for teamId: {} | userId: {}", teamId, userId);
        List<Course> courses = courseRepository.findByTeamIdAndUserId(teamId, userId);

        if (courses.isEmpty()) {
            return new TeamStatsResponse(0.0, List.of());
        }

        double totalCompletion = courses.stream()
                .mapToDouble(course -> calculateCompletionPercentage(userId, course.getId()))
                .average()
                .orElse(0.0);

        // Get all upcoming event types across all courses in the team
        List<TeamEventSummary> allEvents = courses.stream()
                .flatMap(course -> getUpcomingEventTypes(userId, course.getId()).stream())
                .distinct()
                .toList();

        return new TeamStatsResponse(Math.round(totalCompletion * 100.0) / 100.0, allEvents);
    }

    private double calculateCompletionPercentage(String userId, UUID courseId) {
        long total = taskRepository.countByUserIdAndCourseId(userId, courseId);
        if (total == 0) {
            return 0.0;
        }
        long completed = taskRepository.countByUserIdAndCourseIdAndCompletedTrue(userId, courseId);
        return Math.round((completed * 10000.0) / total) / 100.0;
    }

    private List<TeamEventSummary> getUpcomingEventTypes(String userId, UUID courseId) {
        Instant now = Instant.now();
        Instant limit = now.plus(30, java.time.temporal.ChronoUnit.DAYS); // Next 30 days

        return eventRepository.findByUserIdAndCourseIdAndStartDateAfterAndTaskIdIsNotNull(userId, courseId, now)
                .stream()
                .filter(event -> event.getStartDate().isBefore(limit))
                .map(event -> {
                    String eventType = event.getEventType();
                    // Normalize to lowercase like in EventType.wireValue()
                    return new TeamEventSummary(eventType != null ? eventType.toLowerCase() : "event");
                })
                .distinct()
                .collect(Collectors.toList());
    }
}