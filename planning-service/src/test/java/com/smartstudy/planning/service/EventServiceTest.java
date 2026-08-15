package com.smartstudy.planning.service;

import com.smartstudy.planning.dto.request.CreateCourseEventRequest;
import com.smartstudy.planning.dto.request.CreateEventRequest;
import com.smartstudy.planning.dto.request.CreateEventsRequest;
import com.smartstudy.planning.dto.response.AlertResponse;
import com.smartstudy.planning.dto.response.EventsResponse;
import com.smartstudy.planning.model.Course;
import com.smartstudy.planning.model.Event;
import com.smartstudy.planning.model.EventType;
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

    @InjectMocks
    private EventService eventService;

    private final String userId = "test-user";
    private final UUID courseId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        reset(eventRepository, courseRepository, taskRepository);
        lenient().when(eventRepository.save(any(Event.class))).thenAnswer(invocation -> {
            Event e = invocation.getArgument(0);
            if (e.getId() == null) {
                e.setId(UUID.randomUUID());
            }
            return e;
        });
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
}
