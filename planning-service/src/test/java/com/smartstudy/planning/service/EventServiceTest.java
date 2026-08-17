package com.smartstudy.planning.service;

import com.smartstudy.planning.ai.model.ExtractedTask;
import com.smartstudy.planning.ai.model.ScheduleResult;
import com.smartstudy.planning.dto.request.CreateCourseEventRequest;
import com.smartstudy.planning.dto.request.CreateEventRequest;
import com.smartstudy.planning.dto.request.CreateEventsRequest;
import com.smartstudy.planning.ai.model.ScheduledPart;
import com.smartstudy.planning.dto.response.AlertResponse;
import com.smartstudy.planning.dto.response.EventsResponse;
import com.smartstudy.planning.model.Course;
import com.smartstudy.planning.model.Event;
import com.smartstudy.planning.model.EventType;
import com.smartstudy.planning.model.Priority;
import com.smartstudy.planning.model.Task;
import com.smartstudy.planning.repository.CourseRepository;
import com.smartstudy.planning.repository.EventRepository;
import com.smartstudy.planning.repository.TaskRepository;
import com.smartstudy.shared.exception.ConflictException;
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
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class EventServiceTest {

    @Mock
    private EventRepository eventRepository;

    @Mock
    private CourseRepository courseRepository;

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private UserPreferencesService userPreferencesService;

    @Mock
    private StudyPlannerAgent studyPlannerAgent;

    @Mock
    private TaskPriorityService taskPriorityService;

    @Mock
    private RoadmapService roadmapService;

    @InjectMocks
    private EventService eventService;

    private final String userId = "test-user";
    private final UUID courseId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        reset(eventRepository, courseRepository, taskRepository, userPreferencesService, studyPlannerAgent, taskPriorityService, roadmapService);
        lenient().when(eventRepository.save(any(Event.class))).thenAnswer(invocation -> {
            Event e = invocation.getArgument(0);
            if (e.getId() == null) {
                e.setId(UUID.randomUUID());
            }
            return e;
        });
        lenient().when(courseRepository.save(any(Course.class))).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(userPreferencesService.resolve(any(String.class)))
                .thenReturn(new UserPreferencesService.StudyPreferences(60, java.util.EnumSet.allOf(java.time.DayOfWeek.class), 0, java.util.Set.of()));
        lenient().when(roadmapService.reschedule(any(String.class), anyInt(), any(String.class)))
                .thenReturn(null);
    }

    // --- Non-course overlap ---

    @Test
    void testNonCourseEvent_overlapsWithAnotherNonCourseEvent_isSkipped() {
        Instant start = Instant.parse("2026-07-24T10:00:00Z");
        Instant end = Instant.parse("2026-07-24T11:00:00Z");
        Event existing = createEvent("Existing", start, end, null);

        when(eventRepository.findOverlappingEvents(eq(userId), eq((UUID) null), eq(start), eq(end)))
                .thenReturn(List.of(existing));

        CreateEventsRequest request = new CreateEventsRequest(List.of(
                new CreateEventRequest("New Event", start.toString(), end.toString(), "EXAM", false)
        ));

        EventsResponse response = eventService.createEvents(userId, request.events());
        assertTrue(response.success());
        assertEquals(0, response.createdCount());
    }

    @Test
    void testNonCourseEvent_doesNotOverlapWithCourseEvent_succeeds() {
        Instant start = Instant.parse("2026-07-24T10:00:00Z");
        Instant end = Instant.parse("2026-07-24T11:00:00Z");

        when(eventRepository.findOverlappingEvents(eq(userId), eq((UUID) null), eq(start), eq(end)))
                .thenReturn(List.of());

        CreateEventsRequest request = new CreateEventsRequest(List.of(
                new CreateEventRequest("New Event", start.toString(), end.toString(), "EXAM", false)
        ));

        EventsResponse response = eventService.createEvents(userId, request.events());
        assertTrue(response.success());
        assertEquals(1, response.createdCount());
    }

    // --- Course event overlap ---

    @Test
    void testCourseEvent_overlapsWithAnotherCourseEvent_throwsConflict() {
        Instant instant = Instant.parse("2026-07-24T10:00:00Z");
        Event existing = createEvent("Existing Exam", instant, null, courseId);

        when(eventRepository.findOverlappingCourseEventsAt(eq(userId), eq(instant)))
                .thenReturn(List.of(existing));

        CreateCourseEventRequest request = new CreateCourseEventRequest(
                "New Exam", EventType.EXAM.wireValue(), instant.toString()
        );

        assertThrows(ConflictException.class, () -> eventService.createCourseEvent(userId, courseId, request));
    }

    @Test
    void testCourseEvent_doesNotOverlapWithNonCourseEvent_succeeds() {
        Instant instant = Instant.parse("2026-07-24T10:00:00Z");

        when(eventRepository.findOverlappingCourseEventsAt(eq(userId), eq(instant)))
                .thenReturn(List.of());

        CreateCourseEventRequest request = new CreateCourseEventRequest(
                "New Exam", EventType.EXAM.wireValue(), instant.toString()
        );

        AlertResponse response = eventService.createCourseEvent(userId, courseId, request);
        assertNotNull(response);
        assertTrue(response.message().contains("Exam added"));
    }

    @Test
    void testCourseEvent_reschedulesRemainingTasksUsingUserPreferences() {
        Instant instant = Instant.parse("2026-07-24T10:00:00Z");
        Task task = createTask(LocalDate.of(2026, 7, 24));
        ScheduleResult result = new ScheduleResult(List.of(
                new ScheduledPart("Chapter 1", LocalDate.of(2026, 7, 21), 45, 1,
                        null, null, null, List.of(), Priority.HIGH, task.getMaterialId())
        ), List.of(), false);

        when(eventRepository.findOverlappingCourseEventsAt(eq(userId), eq(instant)))
                .thenReturn(List.of());
        when(taskRepository.findByUserIdAndCourseIdAndScheduledDateGreaterThanEqualAndLockedFalseAndCompletedFalseAndMissedFalse(
                eq(userId), eq(courseId), eq(LocalDate.of(2026, 7, 24))))
                .thenReturn(List.of(task));
        when(userPreferencesService.resolve(userId))
                .thenReturn(new UserPreferencesService.StudyPreferences(45,
                        java.util.Set.of(java.time.DayOfWeek.TUESDAY), 0, java.util.Set.of()));
        when(studyPlannerAgent.rescheduleCourseTasks(eq(userId), eq(courseId), eq(List.of(task)), eq(45), eq("TUE"),
                eq(LocalDate.of(2026, 7, 24))))
                .thenReturn(result);

        AlertResponse response = eventService.createCourseEvent(userId, courseId,
                new CreateCourseEventRequest("Exam", EventType.EXAM.wireValue(), instant.toString()));

        assertTrue(response.message().contains("Exam added"));
        verify(studyPlannerAgent).rescheduleCourseTasks(userId, courseId, List.of(task), 45, "TUE",
                LocalDate.of(2026, 7, 24));
    }

    @Test
    void testCourseEvent_alertExplainsPartialReschedule() {
        Instant instant = Instant.parse("2026-07-24T10:00:00Z");
        Task task = createTask(LocalDate.of(2026, 7, 24));
        ExtractedTask unscheduled = new ExtractedTask("Chapter 2", 60, 2, null, List.of(),
                Priority.MEDIUM, task.getMaterialId());
        ScheduleResult result = new ScheduleResult(List.of(), List.of(unscheduled), true);

        when(eventRepository.findOverlappingCourseEventsAt(eq(userId), eq(instant)))
                .thenReturn(List.of());
        when(taskRepository.findByUserIdAndCourseIdAndScheduledDateGreaterThanEqualAndLockedFalseAndCompletedFalseAndMissedFalse(
                eq(userId), eq(courseId), eq(LocalDate.of(2026, 7, 24))))
                .thenReturn(List.of(task));
        when(userPreferencesService.resolve(userId))
                .thenReturn(new UserPreferencesService.StudyPreferences(30,
                        java.util.Set.of(java.time.DayOfWeek.MONDAY), 0, java.util.Set.of()));
        when(studyPlannerAgent.rescheduleCourseTasks(eq(userId), eq(courseId), eq(List.of(task)), eq(30), eq("MON"),
                eq(LocalDate.of(2026, 7, 24))))
                .thenReturn(result);

        AlertResponse response = eventService.createCourseEvent(userId, courseId,
                new CreateCourseEventRequest("Exam", EventType.EXAM.wireValue(), instant.toString()));

        assertTrue(response.message().contains("1 tasks could not fit"));
    }

    @Test
    void testExamCourseEvent_refreshesMaterialTaskPrioritiesFromCourseExamDate() {
        Instant examInstant = Instant.parse("2026-07-24T10:00:00Z");
        Task task = createTask(LocalDate.of(2026, 7, 23));
        task.setPriority(Priority.LOW);
        Course course = Course.builder()
                .id(courseId)
                .userId(userId)
                .name("Biology")
                .startDate(Instant.parse("2026-07-01T00:00:00Z"))
                .examDate(null)
                .hidden(false)
                .build();

        when(courseRepository.findByIdAndUserId(courseId, userId)).thenReturn(Optional.of(course));
        when(eventRepository.findOverlappingCourseEventsAt(eq(userId), eq(examInstant)))
                .thenReturn(List.of());
        when(taskRepository.findByUserIdAndCourseIdAndScheduledDateGreaterThanEqualAndLockedFalseAndCompletedFalseAndMissedFalse(
                eq(userId), eq(courseId), eq(LocalDate.of(2026, 7, 24))))
                .thenReturn(List.of());
        when(taskRepository.findByUserIdAndCourseIdOrderByCreatedAtAsc(userId, courseId))
                .thenReturn(List.of(task));
        when(taskPriorityService.determinePriority(userId, courseId, task.getScheduledDate()))
                .thenReturn(Priority.HIGH);

        AlertResponse response = eventService.createCourseEvent(userId, courseId,
                new CreateCourseEventRequest("Final Exam", EventType.EXAM.wireValue(), examInstant.toString()));

        assertTrue(response.message().contains("Exam added"));
        assertEquals(examInstant, course.getExamDate());
        verify(taskRepository).saveAll(argThat(tasks -> {
            List<Task> savedTasks = StreamSupport.stream(tasks.spliterator(), false).toList();
            return savedTasks.size() == 1 && savedTasks.getFirst().getPriority() == Priority.HIGH;
        }));
    }

    // --- System events ---

    @Test
    void testSystemEvent_duplicateTitleAndDate_isSkipped() {
        Instant start = Instant.parse("2026-07-24T10:00:00Z");
        Instant end = Instant.parse("2026-07-24T11:00:00Z");
        Event existing = createEvent("Holiday", start, end, null);

        when(eventRepository.findByUserIdAndEventTypeAndTitleAndStartDate(eq(userId), eq("system"), eq("Holiday"), eq(start)))
                .thenReturn(Optional.of(existing));

        CreateEventsRequest request = new CreateEventsRequest(List.of(
                new CreateEventRequest("Holiday", start.toString(), end.toString(), "system", false)
        ));

        EventsResponse response = eventService.createEvents(userId, request.events());
        assertTrue(response.success());
        assertEquals(0, response.createdCount());
    }

    @Test
    void testSystemEvent_noDuplicate_succeedsWithoutOverlapCheck() {
        Instant start = Instant.parse("2026-07-24T10:00:00Z");
        Instant end = Instant.parse("2026-07-24T11:00:00Z");

        when(eventRepository.findByUserIdAndEventTypeAndTitleAndStartDate(eq(userId), eq("system"), eq("Holiday"), eq(start)))
                .thenReturn(Optional.empty());
        when(eventRepository.save(any(Event.class)))
                .thenAnswer(invocation -> {
                    Event e = invocation.getArgument(0);
                    e.setId(UUID.randomUUID());
                    return e;
                });

        CreateEventsRequest request = new CreateEventsRequest(List.of(
                new CreateEventRequest("Holiday", start.toString(), end.toString(), "system", false)
        ));

        EventsResponse response = eventService.createEvents(userId, request.events());
        assertTrue(response.success());
        assertEquals(1, response.createdCount());
        verify(eventRepository, never()).findOverlappingEvents(any(), any(), any(), any());
    }

    @Test
    void testSystemEvent_doesNotConflictWithOverlappingNonSystemEvent() {
        Instant start = Instant.parse("2026-07-24T10:00:00Z");
        Instant end = Instant.parse("2026-07-24T11:00:00Z");

        when(eventRepository.findByUserIdAndEventTypeAndTitleAndStartDate(eq(userId), eq("system"), eq("Holiday"), eq(start)))
                .thenReturn(Optional.empty());
        when(eventRepository.save(any(Event.class)))
                .thenAnswer(invocation -> {
                    Event e = invocation.getArgument(0);
                    e.setId(UUID.randomUUID());
                    return e;
                });

        CreateEventsRequest request = new CreateEventsRequest(List.of(
                new CreateEventRequest("Holiday", start.toString(), end.toString(), "system", false)
        ));

        EventsResponse response = eventService.createEvents(userId, request.events());
        assertTrue(response.success());
        verify(eventRepository, never()).findOverlappingEvents(any(), any(), any(), any());
    }

    // --- In-memory batch respects course scope ---

    @Test
    void testInMemoryBatch_nonCourseEventsOverlap_isSkipped() {
        Instant start = Instant.parse("2026-07-24T10:00:00Z");
        Instant end = Instant.parse("2026-07-24T11:00:00Z");

        when(eventRepository.findOverlappingEvents(eq(userId), eq((UUID) null), eq(start), eq(end)))
                .thenReturn(List.of());

        CreateEventsRequest request = new CreateEventsRequest(List.of(
                new CreateEventRequest("Event A", start.toString(), end.toString(), "EXAM", false),
                new CreateEventRequest("Event B", start.toString(), end.toString(), "EXAM", false)
        ));

        EventsResponse response = eventService.createEvents(userId, request.events());
        assertTrue(response.success());
        assertEquals(1, response.createdCount());
        assertEquals("Event A", response.data().getFirst().title());
    }

    @Test
    void testNonCourseEvent_overlapsWithExistingButSecondEventDoesNot_createsOnlySecond() {
        Instant start = Instant.parse("2026-07-24T10:00:00Z");
        Instant end = Instant.parse("2026-07-24T11:00:00Z");
        Event existing = createEvent("Existing", start, end, null);

        when(eventRepository.findOverlappingEvents(eq(userId), eq((UUID) null), eq(start), eq(end)))
                .thenReturn(List.of(existing));

        Instant start2 = Instant.parse("2026-07-24T12:00:00Z");
        Instant end2 = Instant.parse("2026-07-24T13:00:00Z");

        CreateEventsRequest request = new CreateEventsRequest(List.of(
                new CreateEventRequest("Overlapping Event", start.toString(), end.toString(), "EXAM", false),
                new CreateEventRequest("Valid Event", start2.toString(), end2.toString(), "EXAM", false)
        ));

        EventsResponse response = eventService.createEvents(userId, request.events());
        assertTrue(response.success());
        assertEquals(1, response.createdCount());
        assertEquals("Valid Event", response.data().getFirst().title());
    }

    // --- Helpers ---

    private Event createEvent(String title, Instant start, Instant end, UUID courseId) {
        Event event = new Event();
        event.setId(UUID.randomUUID());
        event.setUserId(userId);
        event.setTitle(title);
        event.setStartDate(start);
        event.setEndDate(end);
        event.setCourseId(courseId);
        event.setEventType(EventType.EXAM.wireValue());
        event.setCanStudyThrough(false);
        return event;
    }

    private Task createTask(LocalDate scheduledDate) {
        return Task.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .courseId(courseId)
                .materialId(UUID.randomUUID())
                .title("Chapter 1")
                .durationMinutes(45)
                .priority(Priority.MEDIUM)
                .completed(false)
                .scheduledDate(scheduledDate)
                .sequenceOrder(1)
                .locked(false)
                .missed(false)
                .build();
    }
}
