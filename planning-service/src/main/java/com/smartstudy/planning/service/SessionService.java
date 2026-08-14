package com.smartstudy.planning.service;

import com.smartstudy.planning.dto.request.EndSessionRequest;
import com.smartstudy.planning.dto.request.StartSessionRequest;
import com.smartstudy.planning.dto.response.AlertResponse;
import com.smartstudy.planning.dto.response.SessionResponse;
import com.smartstudy.planning.model.StudySession;
import com.smartstudy.planning.model.Task;
import com.smartstudy.planning.repository.StudySessionRepository;
import com.smartstudy.planning.repository.TaskRepository;
import com.smartstudy.shared.logging.LoggerFactory;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SessionService {

    private static final Logger log = LoggerFactory.getLogger(SessionService.class);
    private final CourseService courseService;
    private final StudySessionRepository studySessionRepository;
    private final TaskRepository taskRepository;

    @Transactional
    public SessionResponse startSession(String userId, StartSessionRequest request) {
        log.info("Starting session for userId: {} | courseId: {}", userId, request.courseId());
        courseService.getOwnedCourse(userId, request.courseId());
        StudySession session = StudySession.builder()
                .userId(userId)
                .courseId(request.courseId())
                .linkedTaskId(request.linkedTaskId())
                .estimatedDurationMinutes(request.estimatedDurationMinutes())
                .build();
        StudySession saved = studySessionRepository.save(session);
        return new SessionResponse(saved.getId(), saved.getCourseId(), saved.getLinkedTaskId(),
                saved.getStartedAt(), null, null, null, null);
    }

    @Transactional
    public SessionResponse endSession(String userId, UUID sessionId, EndSessionRequest request) {
        log.info("Ending session {} for userId: {} | completed: {}", sessionId, userId, request.taskCompleted());
        StudySession session = studySessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "SESSION_NOT_FOUND"));
        Instant endedAt = Instant.now();
        // Prefer the active time reported by the client so that time spent away from
        // the app is not counted; wall-clock elapsed time is only a fallback for
        // older clients that do not send active_spent_time.
        int minutes = request.activeSpentTime() != null
                ? request.activeSpentTime()
                : Math.max(1, (int) Duration.between(session.getStartedAt(), endedAt).toMinutes());
        session.setEndedAt(endedAt);
        session.setDurationMinutesLogged(minutes);
        session.setTaskCompleted(request.taskCompleted());
        session.setCompletionTime(request.completionTime());

        AlertResponse alert = null;
        if (Boolean.TRUE.equals(request.taskCompleted()) && session.getLinkedTaskId() != null) {
            Task task = taskRepository.findByIdAndUserId(session.getLinkedTaskId(), userId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "TASK_NOT_FOUND"));
            task.setCompleted(true);
            alert = new AlertResponse("Great job - '" + task.getTitle() + "' marked complete on your roadmap.");
        }

        return new SessionResponse(session.getId(), session.getCourseId(), session.getLinkedTaskId(),
                session.getStartedAt(), minutes, request.taskCompleted(), request.completionTime(), alert);
    }
}
