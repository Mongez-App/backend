package com.smartstudy.planning.service;

import com.smartstudy.planning.dto.request.CreateEventRequest;
import com.smartstudy.planning.dto.response.EventResponse;
import com.smartstudy.planning.dto.response.EventsResponse;
import com.smartstudy.planning.exception.ValidationException;
import com.smartstudy.planning.model.Event;
import com.smartstudy.planning.repository.EventRepository;
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

@Service
@RequiredArgsConstructor
public class EventService {

    private static final Logger log = LoggerFactory.getLogger(EventService.class);
    private final EventRepository eventRepository;

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
        return new EventResponse(
                event.getId().toString(),
                event.getTitle(),
                event.getStartDate().toString(),
                event.getEndDate() != null ? event.getEndDate().toString() : null
        );
    }
}
