package com.smartstudy.planning.service;

import com.smartstudy.planning.dto.request.CreateCourseEventRequest;
import com.smartstudy.planning.dto.request.CreateEventRequest;
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
import com.smartstudy.shared.logging.LoggerFactory;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EventService {

    private static final Logger log = LoggerFactory.getLogger(EventService.class);
    private final EventRepository eventRepository;
    private final CourseRepository courseRepository;
    private final TaskRepository taskRepository;

    @Transactional
    public EventsResponse createEvents(String userId, List<CreateEventRequest> requests) {
        log.info("Creating {} events for userId: {}", requests.size(), userId);
        List<EventResponse> createdResponses = new ArrayList<>();
        List<String> validationErrors = new ArrayList<>();

        for (CreateEventRequest request : requests) {
            try {
                validateEventRequest(request);
                Instant startInstant = parseInstant(request.startDate());
                Instant endInstant = request.endDate() != null && !request.endDate().isBlank()
                        ? parseInstant(request.endDate())
                        : null;

                if (endInstant != null && endInstant.isBefore(startInstant)) {
                    validationErrors.add("Event '" + request.title() + "': endDate must be equal to or after startDate.");
                    continue;
                }

                Event event = Event.builder()
                        .userId(userId)
                        .title(request.title())
                        .startDate(startInstant)
                        .endDate(endInstant)
                        .eventType(request.eventType())
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

        Event event = Event.builder()
                .userId(userId)
                .title(request.title())
                .startDate(eventInstant)
                .courseId(courseId)
                .eventType(eventType.wireValue())
                .build();
        Event saved = eventRepository.save(event);

        rescheduleCourseTasksBeforeEvent(saved);

        return new AlertResponse(eventType.label()
                + " added! Your AI roadmap has been updated with study tasks.");
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

    private void rescheduleCourseTasksBeforeEvent(Event event) {
        LocalDate eventDate = event.getStartDate().atZone(ZoneOffset.UTC).toLocalDate();
        UUID courseId = event.getCourseId();
        String userId = event.getUserId();

        List<Task> tasksToReschedule = taskRepository
                .findByUserIdAndCourseIdAndScheduledDateGreaterThanEqualAndLockedFalseAndCompletedFalseAndMissedFalse(
                        userId, courseId, eventDate);

        if (tasksToReschedule.isEmpty()) {
            log.info("No tasks to reschedule for course {} before event on {}", courseId, eventDate);
            return;
        }

        log.info("Rescheduling {} tasks for course {} before event on {}",
                tasksToReschedule.size(), courseId, eventDate);

        Map<UUID, List<Task>> byMaterial = tasksToReschedule.stream()
                .collect(Collectors.groupingBy(t -> t.getMaterialId() != null ? t.getMaterialId() : UUID.randomUUID()));

        List<Task> updatedTasks = new ArrayList<>();
        LocalDate assignmentDate = eventDate.minusDays(1);

        List<UUID> materialKeys = new ArrayList<>(byMaterial.keySet());
        materialKeys.sort((a, b) -> {
            List<Task> tasksA = byMaterial.get(a);
            List<Task> tasksB = byMaterial.get(b);
            int minSeqA = tasksA.stream().mapToInt(t -> t.getSequenceOrder() != null ? t.getSequenceOrder() : 0).min().orElse(0);
            int minSeqB = tasksB.stream().mapToInt(t -> t.getSequenceOrder() != null ? t.getSequenceOrder() : 0).min().orElse(0);
            return Integer.compare(minSeqA, minSeqB);
        });

        for (UUID materialKey : materialKeys) {
            List<Task> materialTasks = byMaterial.get(materialKey);
            materialTasks.sort(Comparator.comparingInt(t -> t.getSequenceOrder() != null ? t.getSequenceOrder() : 0));

            for (Task task : materialTasks) {
                LocalDate nearestEventDate = findNearestEventDateAfter(userId, courseId, eventDate, task.getScheduledDate());
                if (nearestEventDate != null && assignmentDate.isAfter(nearestEventDate)) {
                    assignmentDate = nearestEventDate.minusDays(1);
                }
                task.setScheduledDate(assignmentDate);
                updatedTasks.add(task);
                assignmentDate = assignmentDate.minusDays(1);
            }
        }

        taskRepository.saveAll(updatedTasks);
        log.info("Rescheduled {} tasks for course {} before event on {}",
                updatedTasks.size(), courseId, eventDate);
    }

    private LocalDate findNearestEventDateAfter(String userId, UUID courseId, LocalDate fromDate, LocalDate taskDate) {
        LocalDate nearestEventDate = eventRepository.findFirstByUserIdAndCourseIdAndStartDateAfterOrderByStartDateAsc(
                userId, courseId, fromDate.atStartOfDay().toInstant(ZoneOffset.UTC))
                .map(e -> e.getStartDate().atZone(ZoneOffset.UTC).toLocalDate())
                .orElse(null);
        if (nearestEventDate != null && nearestEventDate.isBefore(taskDate)) {
            return null;
        }
        return nearestEventDate;
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
                courseName
        );
    }
}
