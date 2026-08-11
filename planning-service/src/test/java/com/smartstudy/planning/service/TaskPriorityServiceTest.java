package com.smartstudy.planning.service;

import com.smartstudy.planning.model.Course;
import com.smartstudy.planning.model.Event;
import com.smartstudy.planning.model.EventType;
import com.smartstudy.planning.model.Priority;
import com.smartstudy.planning.repository.CourseRepository;
import com.smartstudy.planning.repository.EventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TaskPriorityServiceTest {

    @Mock
    private CourseRepository courseRepository;

    @Mock
    private EventRepository eventRepository;

    @InjectMocks
    private TaskPriorityService taskPriorityService;

    private final String userId = "test-user";
    private final UUID courseId = UUID.randomUUID();
    private final LocalDate today = LocalDate.now();

    @BeforeEach
    void setUp() {
        reset(courseRepository, eventRepository);
    }

    // --- Exam proximity scoring ---

    @Test
    void testHighPriority_whenExamWithin7Days() {
        when(courseRepository.findById(courseId))
                .thenReturn(Optional.of(createCourse(today.plusDays(5))));
        when(eventRepository.findByUserIdAndStartDateBetween(anyString(), any(), any()))
                .thenReturn(List.of());

        Priority priority = taskPriorityService.determinePriority(userId, courseId, today);

        assertEquals(Priority.HIGH, priority);
    }

    @Test
    void testMediumPriority_whenExamWithin14Days() {
        when(courseRepository.findById(courseId))
                .thenReturn(Optional.of(createCourse(today.plusDays(10))));
        when(eventRepository.findByUserIdAndStartDateBetween(anyString(), any(), any()))
                .thenReturn(List.of());

        Priority priority = taskPriorityService.determinePriority(userId, courseId, today);

        assertEquals(Priority.MEDIUM, priority);
    }

    @Test
    void testLowPriority_whenExamFarAway() {
        when(courseRepository.findById(courseId))
                .thenReturn(Optional.of(createCourse(today.plusDays(30))));
        when(eventRepository.findByUserIdAndStartDateBetween(anyString(), any(), any()))
                .thenReturn(List.of());

        Priority priority = taskPriorityService.determinePriority(userId, courseId, today);

        assertEquals(Priority.LOW, priority);
    }

    // --- Event type scoring ---

    @Test
    void testHighPriority_whenNearestEventIsExam() {
        when(courseRepository.findById(courseId))
                .thenReturn(Optional.of(createCourse(today.plusDays(30))));
        when(eventRepository.findByUserIdAndStartDateBetween(anyString(), any(), any()))
                .thenReturn(List.of(createEvent(EventType.EXAM, today.plusDays(2))));

        Priority priority = taskPriorityService.determinePriority(userId, courseId, today);

        assertEquals(Priority.HIGH, priority);
    }

    @Test
    void testMediumPriority_whenNearestEventIsAssignment() {
        when(courseRepository.findById(courseId))
                .thenReturn(Optional.of(createCourse(today.plusDays(30))));
        when(eventRepository.findByUserIdAndStartDateBetween(anyString(), any(), any()))
                .thenReturn(List.of(createEvent(EventType.ASSIGNMENT, today.plusDays(2))));

        Priority priority = taskPriorityService.determinePriority(userId, courseId, today);

        assertEquals(Priority.MEDIUM, priority);
    }

    @Test
    void testMediumPriority_whenNearestEventIsQuiz() {
        when(courseRepository.findById(courseId))
                .thenReturn(Optional.of(createCourse(today.plusDays(30))));
        when(eventRepository.findByUserIdAndStartDateBetween(anyString(), any(), any()))
                .thenReturn(List.of(createEvent(EventType.QUIZ, today.plusDays(2))));

        Priority priority = taskPriorityService.determinePriority(userId, courseId, today);

        assertEquals(Priority.MEDIUM, priority);
    }

    @Test
    void testMediumPriority_whenNearestEventIsProject() {
        when(courseRepository.findById(courseId))
                .thenReturn(Optional.of(createCourse(today.plusDays(30))));
        when(eventRepository.findByUserIdAndStartDateBetween(anyString(), any(), any()))
                .thenReturn(List.of(createEvent(EventType.PROJECT, today.plusDays(2))));

        Priority priority = taskPriorityService.determinePriority(userId, courseId, today);

        assertEquals(Priority.MEDIUM, priority);
    }

    @Test
    void testMediumPriority_whenNearestEventIsMidterm() {
        when(courseRepository.findById(courseId))
                .thenReturn(Optional.of(createCourse(today.plusDays(30))));
        when(eventRepository.findByUserIdAndStartDateBetween(anyString(), any(), any()))
                .thenReturn(List.of(createEvent(EventType.MIDTERM, today.plusDays(2))));

        Priority priority = taskPriorityService.determinePriority(userId, courseId, today);

        assertEquals(Priority.MEDIUM, priority);
    }

    @Test
    void testLowPriority_whenNoUpcomingEvents() {
        when(courseRepository.findById(courseId))
                .thenReturn(Optional.of(createCourse(today.plusDays(30))));
        when(eventRepository.findByUserIdAndStartDateBetween(anyString(), any(), any()))
                .thenReturn(List.of());

        Priority priority = taskPriorityService.determinePriority(userId, courseId, today);

        assertEquals(Priority.LOW, priority);
    }

    @Test
    void testLowPriority_whenInvalidEventType() {
        when(courseRepository.findById(courseId))
                .thenReturn(Optional.of(createCourse(today.plusDays(30))));
        Event invalidEvent = new Event();
        invalidEvent.setEventType("invalid_type");
        invalidEvent.setStartDate(today.plusDays(2).atStartOfDay(ZoneOffset.UTC).toInstant());
        when(eventRepository.findByUserIdAndStartDateBetween(anyString(), any(), any()))
                .thenReturn(List.of(invalidEvent));

        Priority priority = taskPriorityService.determinePriority(userId, courseId, today);

        assertEquals(Priority.LOW, priority);
    }

    // --- Max scoring ---

    @Test
    void testHighPriority_whenExamMediumButEventIsHigh() {
        when(courseRepository.findById(courseId))
                .thenReturn(Optional.of(createCourse(today.plusDays(10))));
        when(eventRepository.findByUserIdAndStartDateBetween(anyString(), any(), any()))
                .thenReturn(List.of(createEvent(EventType.EXAM, today.plusDays(2))));

        Priority priority = taskPriorityService.determinePriority(userId, courseId, today);

        assertEquals(Priority.HIGH, priority);
    }

    @Test
    void testMediumPriority_whenExamHighButEventIsMedium() {
        when(courseRepository.findById(courseId))
                .thenReturn(Optional.of(createCourse(today.plusDays(5))));
        when(eventRepository.findByUserIdAndStartDateBetween(anyString(), any(), any()))
                .thenReturn(List.of(createEvent(EventType.ASSIGNMENT, today.plusDays(2))));

        Priority priority = taskPriorityService.determinePriority(userId, courseId, today);

        assertEquals(Priority.HIGH, priority);
    }

    @Test
    void testMediumPriority_whenExamLowButEventIsMedium() {
        when(courseRepository.findById(courseId))
                .thenReturn(Optional.of(createCourse(today.plusDays(30))));
        when(eventRepository.findByUserIdAndStartDateBetween(anyString(), any(), any()))
                .thenReturn(List.of(createEvent(EventType.ASSIGNMENT, today.plusDays(2))));

        Priority priority = taskPriorityService.determinePriority(userId, courseId, today);

        assertEquals(Priority.MEDIUM, priority);
    }

    // --- No course (null courseId) ---

    @Test
    void testLowPriority_whenNoCourseAndNoEvents() {
        when(eventRepository.findByUserIdAndStartDateBetween(anyString(), any(), any()))
                .thenReturn(List.of());

        Priority priority = taskPriorityService.determinePriority(userId, null, today);

        assertEquals(Priority.LOW, priority);
    }

    @Test
    void testHighPriority_whenNoCourseButNearestEventIsExam() {
        when(eventRepository.findByUserIdAndStartDateBetween(anyString(), any(), any()))
                .thenReturn(List.of(createEvent(EventType.EXAM, today.plusDays(2))));

        Priority priority = taskPriorityService.determinePriority(userId, null, today);

        assertEquals(Priority.HIGH, priority);
    }

    private Course createCourse(LocalDate examDate) {
        Course course = new Course();
        course.setId(courseId);
        course.setUserId(userId);
        course.setExamDate(examDate.atStartOfDay(ZoneOffset.UTC).toInstant());
        return course;
    }

    private Event createEvent(EventType type, LocalDate startDate) {
        Event event = new Event();
        event.setId(UUID.randomUUID());
        event.setUserId(userId);
        event.setTitle("Test Event");
        event.setStartDate(startDate.atStartOfDay(ZoneOffset.UTC).toInstant());
        event.setEventType(type.wireValue());
        event.setCanStudyThrough(false);
        return event;
    }
}
