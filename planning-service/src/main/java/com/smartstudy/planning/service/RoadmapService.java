package com.smartstudy.planning.service;

import com.smartstudy.planning.ai.model.AgentCheckResult;
import com.smartstudy.planning.dto.response.AlertResponse;
import com.smartstudy.planning.dto.response.RoadmapResponse;
import com.smartstudy.planning.model.Course;
import com.smartstudy.planning.model.Event;
import com.smartstudy.planning.model.StudyBlock;
import com.smartstudy.planning.repository.CourseRepository;
import com.smartstudy.planning.repository.EventRepository;
import com.smartstudy.planning.repository.StudyBlockRepository;
import com.smartstudy.planning.dto.response.EventResponse;
import java.time.ZoneOffset;
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
    private final StudyBlockRepository studyBlockRepository;
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
        LocalDate endDate = startDate.plusDays(6);
        List<StudyBlock> blocks = studyBlockRepository
                .findByUserIdAndScheduledDateBetweenOrderByScheduledDateAscCreatedAtAsc(userId, startDate, endDate);
        Map<UUID, Course> courses = courseRepository.findByUserIdOrderByCreatedAtAsc(userId)
                .stream()
                .collect(Collectors.toMap(Course::getId, Function.identity()));

        List<Event> events = eventRepository.findByUserIdAndStartDateBetween(
                userId, 
                startDate.atStartOfDay().toInstant(ZoneOffset.UTC), 
                endDate.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC)
        );

        Map<LocalDate, List<StudyBlock>> byDay = blocks.stream()
                .collect(Collectors.groupingBy(StudyBlock::getScheduledDate));

        List<RoadmapResponse.DayResponse> days = byDay.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> toDayResponse(entry.getKey(), entry.getValue(), courses, events))
                .toList();

        RoadmapResponse.WeekResponse week = new RoadmapResponse.WeekResponse(1, startDate, endDate, days);
        return new RoadmapResponse(startDate, List.of(week), alert);
    }

    private RoadmapResponse.DayResponse toDayResponse(LocalDate date, List<StudyBlock> blocks, Map<UUID, Course> courses, List<Event> allEvents) {
        List<RoadmapResponse.StudyBlockResponse> blockResponses = blocks.stream()
                .sorted(Comparator.comparing(StudyBlock::getCreatedAt))
                .map(block -> {
                    Course course = courses.get(block.getCourseId());
                    String courseName = course != null ? course.getName() : "Unknown Course";
                    
                    List<EventResponse> blockEvents = allEvents.stream()
                            .filter(e -> e.getCourseId().equals(block.getCourseId()))
                            .filter(e -> e.getStartDate().atZone(ZoneOffset.UTC).toLocalDate().equals(date))
                            .map(e -> new EventResponse(e.getId().toString(), e.getTitle(), e.getStartDate().toString(),
                                    e.getEndDate() != null ? e.getEndDate().toString() : null, e.getCourseId(), courseName))
                            .toList();

                    return new RoadmapResponse.StudyBlockResponse(block.getId(), block.getCourseId(), courseName, block.getTopic(),
                            block.getDurationMinutes(), block.isCompleted(), blockEvents);
                })
                .toList();
        return new RoadmapResponse.DayResponse(date,
                date.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH), blockResponses);
    }

    private LocalDate weekStart(LocalDate date) {
        return date.with(DayOfWeek.MONDAY);
    }
}
