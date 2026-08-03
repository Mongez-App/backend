package com.smartstudy.planning.controller;

import com.smartstudy.planning.dto.response.InternalProfileStatsResponse;
import com.smartstudy.planning.repository.StudySessionRepository;
import com.smartstudy.planning.repository.TaskRepository;
import com.smartstudy.shared.logging.LoggerFactory;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/internal/users")
@RequiredArgsConstructor
public class InternalUserController {

    private static final Logger log = LoggerFactory.getLogger(InternalUserController.class);
    private final TaskRepository taskRepository;
    private final StudySessionRepository studySessionRepository;

    @GetMapping("/{userId}/stats")
    public InternalProfileStatsResponse getUserStats(@PathVariable("userId") String userId) {
        log.info("Internal request: GET /internal/users/{}/stats", userId);

        long completedTasks = taskRepository.findByUserIdOrderByCreatedAtAsc(userId)
                .stream()
                .filter(task -> task.isCompleted())
                .count();

        long loggedMinutes = studySessionRepository.findByUserId(userId)
                .stream()
                .filter(session -> session.getDurationMinutesLogged() != null)
                .mapToLong(session -> session.getDurationMinutesLogged())
                .sum();

        int studyHours = (int) (loggedMinutes / 60);

        LocalDate today = LocalDate.now();
        boolean completedToday = taskRepository.findByUserIdAndScheduledDateOrderByCreatedAtAsc(userId, today)
                .stream()
                .anyMatch(task -> task.isCompleted());

        int streak = completedToday ? 1 : 0;

        return new InternalProfileStatsResponse(studyHours, (int) completedTasks, streak);
    }
}
