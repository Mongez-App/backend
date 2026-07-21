package com.smartstudy.planning.service;

import com.smartstudy.planning.dto.request.RescheduleRoadmapRequest;
import com.smartstudy.planning.dto.response.AlertResponse;
import com.smartstudy.planning.dto.response.RoadmapResponse;
import com.smartstudy.planning.model.Course;
import com.smartstudy.planning.model.StudyBlock;
import com.smartstudy.planning.repository.CourseRepository;
import com.smartstudy.planning.repository.StudyBlockRepository;
import lombok.RequiredArgsConstructor;
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

    private final StudyBlockRepository studyBlockRepository;
    private final CourseRepository courseRepository;

    @Transactional(readOnly = true)
    public RoadmapResponse getWeeklyRoadmap(String userId, LocalDate startDate) {
        return buildWeeklyResponse(userId, weekStart(startDate), null);
    }

    @Transactional
    public RoadmapResponse reschedule(String userId, RescheduleRoadmapRequest request) {
        List<StudyBlock> blocks = studyBlockRepository.findByIdInAndUserId(request.blockIds(), userId);
        LocalDate targetDate = LocalDate.now().plusDays(1);
        blocks.forEach(block -> block.setScheduledDate(targetDate));
        LocalDate startDate = weekStart(targetDate);
        String message = blocks.size() + " study block" + (blocks.size() == 1 ? " was" : "s were")
                + " moved to " + targetDate.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH) + ".";
        return buildWeeklyResponse(userId, startDate, new AlertResponse(message));
    }

    private RoadmapResponse buildWeeklyResponse(String userId, LocalDate startDate, AlertResponse alert) {
        LocalDate endDate = startDate.plusDays(6);
        List<StudyBlock> blocks = studyBlockRepository
                .findByUserIdAndScheduledDateBetweenOrderByScheduledDateAscCreatedAtAsc(userId, startDate, endDate);
        Map<UUID, Course> courses = courseRepository.findByUserIdOrderByCreatedAtAsc(userId)
                .stream()
                .collect(Collectors.toMap(Course::getId, Function.identity()));
        Map<LocalDate, List<StudyBlock>> byDay = blocks.stream()
                .collect(Collectors.groupingBy(StudyBlock::getScheduledDate));

        List<RoadmapResponse.DayResponse> days = byDay.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> toDayResponse(entry.getKey(), entry.getValue(), courses))
                .toList();

        RoadmapResponse.WeekResponse week = new RoadmapResponse.WeekResponse(1, startDate, endDate, days);
        return new RoadmapResponse(startDate, List.of(week), alert);
    }

    private RoadmapResponse.DayResponse toDayResponse(LocalDate date, List<StudyBlock> blocks, Map<UUID, Course> courses) {
        List<RoadmapResponse.StudyBlockResponse> blockResponses = blocks.stream()
                .sorted(Comparator.comparing(StudyBlock::getCreatedAt))
                .map(block -> {
                    Course course = courses.get(block.getCourseId());
                    String courseName = course != null ? course.getName() : "Unknown Course";
                    return new RoadmapResponse.StudyBlockResponse(block.getId(), courseName, block.getTopic(),
                            block.getDurationMinutes(), block.isCompleted());
                })
                .toList();
        return new RoadmapResponse.DayResponse(date,
                date.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH), blockResponses);
    }

    private LocalDate weekStart(LocalDate date) {
        return date.with(DayOfWeek.MONDAY);
    }
}
