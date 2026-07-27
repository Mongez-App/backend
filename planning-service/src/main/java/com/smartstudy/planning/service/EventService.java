package com.smartstudy.planning.service;

import com.smartstudy.planning.dto.request.CreateEventRequest;
import com.smartstudy.planning.dto.response.AlertResponse;
import com.smartstudy.planning.model.Event;
import com.smartstudy.planning.repository.EventRepository;
import com.smartstudy.shared.logging.LoggerFactory;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EventService {

    private static final Logger log = LoggerFactory.getLogger(EventService.class);
    private final EventRepository eventRepository;

    @Transactional
    public AlertResponse createEvent(String userId, UUID courseId, CreateEventRequest request) {
        log.info("Creating event for userId: {}, courseId: {}", userId, courseId);
        
        Event event = Event.builder()
                .userId(userId)
                .courseId(courseId)
                .title(request.title())
                .eventType(request.eventType())
                .eventDate(request.eventDate())
                .build();
                
        eventRepository.save(event);
        
        log.info("Mocking AI worker call for rescheduling roadmap to include event: {}", event.getId());
        
        String message = request.title() + " added! Your AI roadmap has been updated with study tasks to prepare for this milestone.";
        
        return new AlertResponse(message);
    }
}
