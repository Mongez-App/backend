package com.smartstudy.planning.service;

import com.smartstudy.planning.ai.model.ScheduleResult;
import com.smartstudy.planning.dto.request.CreateCourseEventRequest;
import com.smartstudy.planning.dto.request.CreateEventRequest;
import com.smartstudy.planning.dto.request.GetEventsRequest;
import com.smartstudy.planning.dto.response.AlertResponse;
import com.smartstudy.planning.dto.response.EventResponse;
import com.smartstudy.planning.dto.response.EventsResponse;
import com.smartstudy.planning.exception.ValidationException;
import com.smartstudy.planning.model.Course;
import com.smartstudy.planning.model.Event;
import com.smartstudy.planning.model.EventType;
import com.smartstudy.planning.model.Task;
import com.smartstudy.planning.repository.CourseRepository;
import com.smartstudy.planning.repository.EventRepository;
import com.smartstudy.planning.repository.TaskRepository;
import com.smartstudy.shared.exception.ConflictException;
import com.smartstudy.shared.logging.LoggerFactory;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EventService {

    private static final Logger log = LoggerFactory.getLogger(EventService.class);
    private final EventRepository eventRepository;
    private final CourseRepository courseRepository;
    private final TaskRepository taskRepository;
    private final UserPreferencesService userPreferencesService;
    private final StudyPlannerAgent studyPlannerAgent;

    @Transactional(readOnly = true)
    public List<EventResponse> getEvents(String userId, GetEventsRequest request) {
        log.info("Fetching events for userId: {} | startDate: {} | endDate: {}", userId, request.startDate(), request.endDate());

        List<Event> events;
        if (request.startDate() != null && request.endDate() != null) {
            Instant startInstant = parseInstant(request.startDate());
            Instant endInstant = parseInstant(request.endDate());
            events = eventRepository.findByUserIdAndStartDateBetween(userId, startInstant, endInstant);
        } else if (request.startDate() != null) {
            Instant startInstant = parseInstant(request.startDate());
            events = eventRepository.findByUserIdAndStartDateGreaterThanEqual(userId, startInstant);
        } else if (request.endDate() != null) {
            Instant endInstant = parseInstant(request.endDate());
            events = eventRepository.findByUserIdAndStartDateLessThanEqual(userId, endInstant);
        } else {
            events = eventRepository.findByUserId(userId);
        }

        return events.stream()
                .map(this::toEventResponse)
                .toList();
    }

    @Transactional
    public EventsResponse createEvents(String userId, List<CreateEventRequest> requests) {
        log.info("Creating {} events for userId: {}", requests.size(), userId);
        List<EventResponse> createdResponses = new ArrayList<>();
        List<String> validationErrors = new ArrayList<>();
        List<Instant[]> validatedRanges = new ArrayList<>();

        for (CreateEventRequest request : requests) {
            try {
                validateEventRequest(request);
                Instant startInstant = parseInstant(request.startDate());
                Instant endInstant = parseInstant(request.endDate());

                if (endInstant.isBefore(startInstant)) {
                    validationErrors.add("Event '" + request.title() + "': endDate must be equal to or after startDate.");
                    continue;
                }

                if ("system".equals(request.eventType())) {
                    boolean isDuplicate = !eventRepository.findByUserIdAndEventTypeAndTitleAndStartDate(userId, "system", request.title(), startInstant).isEmpty();
                    if (isDuplicate) {
                        log.warn("Skipping duplicate system event '{}' for user {} on {}", request.title(), userId, startInstant);
                        continue;
                    }
                    Event event = Event.builder()
                            .userId(userId)
                            .title(request.title())
                            .startDate(startInstant)
                            .endDate(endInstant)
                            .eventType(request.eventType())
                            .canStudyThrough(request.canStudyThrough())
                            .build();
                    Event saved = eventRepository.save(event);
                    createdResponses.add(toEventResponse(saved));
                    continue;
                }

                List<Event> overlapping = eventRepository.findOverlappingEvents(userId, null, startInstant, endInstant);
                if (!overlapping.isEmpty()) {
                    log.warn("Skipping overlapping event '{}' for user {} (overlaps with '{}')", request.title(), userId, overlapping.getFirst().getTitle());
                    continue;
                }

                boolean overlapsWithBatch = false;
                for (Instant[] range : validatedRanges) {
                    if (rangesOverlap(range[0], range[1], null, startInstant, endInstant, null)) {
                        overlapsWithBatch = true;
                        break;
                    }
                }
                if (overlapsWithBatch) {
                    log.warn("Skipping event '{}' for user {} (overlaps with another event in same request)", request.title(), userId);
                    continue;
                }
                validatedRanges.add(new Instant[]{startInstant, endInstant});

                Event event = Event.builder()
                        .userId(userId)
                        .title(request.title())
                        .startDate(startInstant)
                        .endDate(endInstant)
                        .eventType(request.eventType())
                        .canStudyThrough(request.canStudyThrough())
                        .build();
                Event saved = eventRepository.save(event);
                createdResponses.add(toEventResponse(saved));
            } catch (ValidationException ex) {
                validationErrors.addAll(ex.getDetails());
            }
        }

        if (!validationErrors.isEmpty()) {
            throw new ValidationException("Validation failed for one or more events.", validationErrors);
        }

        return new EventsResponse(
                true,
                "Events created successfully",
                createdResponses.size(),
                createdResponses
        );
    }

    @Transactional
    public AlertResponse createCourseEvent(String userId, UUID courseId, CreateCourseEventRequest request) {
        log.info("Creating event for userId: {} | courseId: {}", userId, courseId);

        EventType eventType = EventType.fromWireValue(request.eventType())
                .orElseThrow(() -> new ValidationException("Invalid event data.",
                        List.of("'event_type' must be one of: " + EventType.allowedValues())));
        Instant eventInstant = parseEventDate(request.eventDate());

        List<Event> overlapping = eventRepository.findOverlappingCourseEventsAt(userId, eventInstant);
        if (!overlapping.isEmpty()) {
            throw new ConflictException("OVERLAP_EVENT",
                    "Event '" + request.title() + "' overlaps with existing event: " + overlapping.getFirst().getTitle());
        }

        Event event = Event.builder()
                .userId(userId)
                .title(request.title())
                .startDate(eventInstant)
                .courseId(courseId)
                .eventType(eventType.wireValue())
                .build();
        Event saved = eventRepository.save(event);

        ScheduleResult rescheduleResult = rescheduleCourseTasksBeforeEvent(saved);
        String message = eventType.label() + " added! Your AI roadmap has been updated with study tasks.";
        if (rescheduleResult != null && rescheduleResult.overCapacity()) {
            message += " " + rescheduleResult.unscheduledTasks().size()
                    + " tasks could not fit in your available study time; increase your daily minutes, add study days, or adjust deadlines.";
        }

        return new AlertResponse(message);
    }

    /** POST /courses/{id}/events takes an ISO-8601 UTC instant, e.g. 2026-07-24T20:00:00Z. */
    private Instant parseEventDate(String eventDate) {
        try {
            return Instant.parse(eventDate);
        } catch (Exception e) {
            throw new ValidationException("Invalid event data.",
                    List.of("'event_date' must be an ISO-8601 UTC datetime, e.g. 2026-07-24T20:00:00Z."));
        }
    }

    private ScheduleResult rescheduleCourseTasksBeforeEvent(Event event) {
        LocalDate eventDate = event.getStartDate().atZone(ZoneOffset.UTC).toLocalDate();
        UUID courseId = event.getCourseId();
        String userId = event.getUserId();

        List<Task> tasksToReschedule = taskRepository
                .findByUserIdAndCourseIdAndScheduledDateGreaterThanEqualAndLockedFalseAndCompletedFalseAndMissedFalse(
                        userId, courseId, eventDate);

        if (tasksToReschedule.isEmpty()) {
            log.info("No tasks to reschedule for course {} before event on {}", courseId, eventDate);
            return null;
        }

        log.info("Rescheduling {} tasks for course {} before event on {}",
                tasksToReschedule.size(), courseId, eventDate);

        UserPreferencesService.StudyPreferences preferences = userPreferencesService.resolve(userId);
        ScheduleResult result = studyPlannerAgent.rescheduleCourseTasks(userId, courseId, tasksToReschedule,
                preferences.dailyStudyMinutes(), preferences.preferredDays(), eventDate);
        log.info("Rescheduled {} task parts for course {} before event on {} ({} unscheduled)",
                result.scheduledParts().size(), courseId, eventDate, result.unscheduledTasks().size());
        return result;
    }

    private boolean rangesOverlap(Instant s1, Instant e1, UUID courseId1, Instant s2, Instant e2, UUID courseId2) {
        if ((courseId1 == null) != (courseId2 == null)) {
            return false;
        }
        return s1.isBefore(e2) && s2.isBefore(e1);
    }

    private void validateEventRequest(CreateEventRequest request) {
        List<String> errors = new ArrayList<>();
        if (request.title() == null || request.title().isBlank()) {
            errors.add("'title' field is required");
        }
        if (request.startDate() == null || request.startDate().isBlank()) {
            errors.add("'startDate' field is required");
        }
        if (!errors.isEmpty()) {
            throw new ValidationException("Invalid event data.", errors);
        }
    }

    private Instant parseInstant(String dateTime) {
        if (dateTime == null || dateTime.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(dateTime);
        } catch (Exception e) {
            try {
                LocalDate localDate = LocalDate.parse(dateTime);
                return localDate.atStartOfDay(ZoneOffset.UTC).toInstant();
            } catch (Exception ex) {
                throw new ValidationException("Invalid date format: " + dateTime, List.of("Invalid date format: " + dateTime + ". Expected ISO 8601 datetime or date."));
            }
        }
    }

    private EventResponse toEventResponse(Event event) {
        String courseName = null;
        if (event.getCourseId() != null) {
            courseName = courseRepository.findById(event.getCourseId()).map(Course::getName).orElse(null);
        }
        return new EventResponse(
                event.getId().toString(),
                event.getTitle(),
                event.getStartDate().toString(),
                event.getEndDate() != null ? event.getEndDate().toString() : null,
                event.getCourseId(),
                courseName,
                event.getCanStudyThrough(),
                event.getEventType() != null && "system".equals(event.getEventType())
        );
    }
}
