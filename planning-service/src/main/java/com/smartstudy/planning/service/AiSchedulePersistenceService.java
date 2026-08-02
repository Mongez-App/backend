package com.smartstudy.planning.service;

import com.smartstudy.planning.ai.model.ScheduledPart;
import com.smartstudy.planning.dto.response.AlertResponse;
import com.smartstudy.planning.model.Event;
import com.smartstudy.planning.model.Material;
import com.smartstudy.planning.model.Priority;
import com.smartstudy.planning.model.Task;
import com.smartstudy.planning.repository.EventRepository;
import com.smartstudy.planning.repository.CourseRepository;
import com.smartstudy.planning.repository.MaterialRepository;
import com.smartstudy.planning.repository.TaskRepository;
import com.smartstudy.shared.logging.LoggerFactory;
import com.smartstudy.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AiSchedulePersistenceService {

    private static final Logger log = LoggerFactory.getLogger(AiSchedulePersistenceService.class);
    private static final int DEDUP_DURATION_TOLERANCE_MINUTES = 5;
    private final TaskRepository taskRepository;
    private final EventRepository eventRepository;
    private final MaterialRepository materialRepository;
    private final CourseRepository courseRepository;

    public PersistResult persist(String userId, UUID courseId, UUID materialId, List<ScheduledPart> parts, boolean isIncremental) {
        verifyCourseOwnership(userId, courseId);
        if (materialId != null) {
            verifyMaterialOwnership(userId, materialId);
        }

        List<ScheduledPart> validParts = new ArrayList<>();
        List<Task> tasks = new ArrayList<>();
        List<Event> events = new ArrayList<>();
        int skippedCount = 0;
        int conflictCount = 0;

        if (isIncremental) {
            Set<String> existingKeys = buildExistingTaskKeys(userId, courseId, materialId);
            for (ScheduledPart part : parts) {
                String key = dedupKey(part);
                if (existingKeys.contains(key)) {
                    skippedCount++;
                    log.warn("Skipping duplicate task for incremental plan: {} on {}", part.title(), part.date());
                    continue;
                }
                tasks.add(buildTask(userId, courseId, materialId, part));
                validParts.add(part);
            }
        } else {
            for (ScheduledPart part : parts) {
                tasks.add(buildTask(userId, courseId, materialId, part));
                validParts.add(part);
            }
        }

        List<Task> filteredTasks = new ArrayList<>();
        List<ScheduledPart> filteredParts = new ArrayList<>();
        for (int i = 0; i < tasks.size(); i++) {
            Task task = tasks.get(i);
            ScheduledPart part = validParts.get(i);
            if (isOverlappingCompletedMissed(userId, courseId, task)) {
                conflictCount++;
                log.warn("Skipping task on {} due to overlap with completed/missed task", task.getScheduledDate());
            } else {
                filteredTasks.add(task);
                filteredParts.add(part);
            }
        }
        skippedCount += conflictCount;

        List<Task> savedTasks = taskRepository.saveAll(filteredTasks);

        for (int i = 0; i < savedTasks.size(); i++) {
            Task task = savedTasks.get(i);
            ScheduledPart part = filteredParts.get(i);
            LocalDateTime start = part.date().atTime(9, 0);
            LocalDateTime end = start.plusMinutes(part.minutes());
            Event event = Event.builder()
                    .userId(userId)
                    .title(part.title())
                    .startDate(start.atZone(ZoneId.systemDefault()).toInstant())
                    .endDate(end.atZone(ZoneId.systemDefault()).toInstant())
                    .courseId(courseId)
                    .taskId(task.getId())
                    .build();
            events.add(event);
        }

        eventRepository.saveAll(events);

        if (materialId != null) {
            Material material = materialRepository.findByIdAndUserId(materialId, userId)
                    .orElseThrow(() -> new IllegalArgumentException("Material not found: " + materialId));
            material.setStatus("scheduled");
            materialRepository.save(material);
        }

        log.info("Persisted {} tasks and {} events for material {} (incremental={}, skipped={})",
                savedTasks.size(), events.size(), materialId, isIncremental, skippedCount);

        return new PersistResult(savedTasks.size(), events.size(), skippedCount, conflictCount);
    }

    @Transactional
    public void fullReschedule(String userId, UUID courseId) {
        verifyCourseOwnership(userId, courseId);

        LocalDate today = LocalDate.now();
        List<Task> futureTasks = taskRepository.findByUserIdAndCourseIdAndScheduledDateAfterAndCompletedFalseAndMissedFalse(
                userId, courseId, today);

        List<UUID> deletedTaskIds = futureTasks.stream().map(Task::getId).toList();

        List<Event> allLinkedEvents = eventRepository.findByUserIdAndCourseIdAndTaskIdIsNotNull(
                userId, courseId);
        List<Event> linkedEvents = allLinkedEvents.stream()
                .filter(e -> e.getTaskId() != null && deletedTaskIds.contains(e.getTaskId()))
                .toList();

        log.info("Full reschedule: deleting {} future tasks and {} linked events", futureTasks.size(), linkedEvents.size());

        eventRepository.deleteAll(linkedEvents);
        taskRepository.deleteAll(futureTasks);
    }

    private void verifyCourseOwnership(String userId, UUID courseId) {
        courseRepository.findByIdAndUserId(courseId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "COURSE_NOT_OWNED"));
    }

    private void verifyMaterialOwnership(String userId, UUID materialId) {
        materialRepository.findByIdAndUserId(materialId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "MATERIAL_NOT_OWNED"));
    }

    private Set<String> buildExistingTaskKeys(String userId, UUID courseId, UUID materialId) {
        List<Task> existing = materialId != null
                ? taskRepository.findByUserIdAndCourseIdAndMaterialIdAndScheduledDateBetween(
                        userId, courseId, materialId, LocalDate.now(), LocalDate.now().plusYears(1))
                : taskRepository.findByUserIdAndCourseIdAndScheduledDateBetweenOrderByScheduledDateAscCreatedAtAsc(
                        userId, courseId, LocalDate.now(), LocalDate.now().plusYears(1));
        Set<String> keys = new HashSet<>();
        for (Task task : existing) {
            keys.add(dedupKey(task));
        }
        return keys;
    }

    private String dedupKey(ScheduledPart part) {
        return part.title() + "|" + part.date() + "|" + part.minutes();
    }

    private String dedupKey(Task task) {
        return task.getTitle() + "|" + task.getScheduledDate() + "|" + task.getDurationMinutes();
    }

    private Task buildTask(String userId, UUID courseId, UUID materialId, ScheduledPart part) {
        return Task.builder()
                .userId(userId)
                .courseId(courseId)
                .materialId(materialId)
                .title(part.title())
                .durationMinutes(part.minutes())
                .priority(part.priority() != null ? part.priority() : Priority.MEDIUM)
                .completed(false)
                .scheduledDate(part.date())
                .sequenceOrder(part.sequence())
                .splitPart(part.splitPart())
                .totalParts(part.totalParts())
                .locked(true)
                .missed(false)
                .build();
    }

    private List<Task> filterOverlappingCompletedMissed(String userId, UUID courseId, List<Task> tasks) {
        if (tasks.isEmpty()) {
            return tasks;
        }
        LocalDate minDate = tasks.stream().map(Task::getScheduledDate).min(LocalDate::compareTo).orElse(LocalDate.now());
        LocalDate maxDate = tasks.stream().map(Task::getScheduledDate).max(LocalDate::compareTo).orElse(LocalDate.now());

        List<Task> completedOnDates = taskRepository.findByUserIdAndCourseIdAndScheduledDateBetweenAndCompletedTrue(
                userId, courseId, minDate, maxDate);
        List<Task> missedOnDates = taskRepository.findByUserIdAndCourseIdAndScheduledDateBetweenAndMissedTrue(
                userId, courseId, minDate, maxDate);

        Set<LocalDate> blockedDates = new HashSet<>();
        for (Task t : completedOnDates) {
            blockedDates.add(t.getScheduledDate());
        }
        for (Task t : missedOnDates) {
            blockedDates.add(t.getScheduledDate());
        }

        List<Task> filtered = new ArrayList<>();
        for (Task task : tasks) {
            if (blockedDates.contains(task.getScheduledDate())) {
                log.warn("Skipping task on {} due to overlap with completed/missed task", task.getScheduledDate());
            } else {
                filtered.add(task);
            }
        }
        return filtered;
    }

    private boolean isOverlappingCompletedMissed(String userId, UUID courseId, Task task) {
        List<Task> completedOnDate = taskRepository.findByUserIdAndCourseIdAndScheduledDateBetweenAndCompletedTrue(
                userId, courseId, task.getScheduledDate(), task.getScheduledDate());
        if (!completedOnDate.isEmpty()) {
            return true;
        }
        List<Task> missedOnDate = taskRepository.findByUserIdAndCourseIdAndScheduledDateBetweenAndMissedTrue(
                userId, courseId, task.getScheduledDate(), task.getScheduledDate());
        return !missedOnDate.isEmpty();
    }

    public record PersistResult(int createdCount, int eventCount, int skippedCount, int conflictCount) {
        public PersistResult(int createdCount, int eventCount, int skippedCount) {
            this(createdCount, eventCount, skippedCount, 0);
        }
    }
}
