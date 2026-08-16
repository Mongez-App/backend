package com.smartstudy.planning.service;

import com.smartstudy.planning.ai.model.ScheduledPart;
import com.smartstudy.planning.model.Event;
import com.smartstudy.planning.model.Material;
import com.smartstudy.planning.model.MaterialStatus;
import com.smartstudy.planning.model.Priority;
import com.smartstudy.planning.model.Task;
import com.smartstudy.planning.repository.EventRepository;
import com.smartstudy.planning.repository.MaterialRepository;
import com.smartstudy.planning.repository.TaskRepository;
import com.smartstudy.shared.logging.LoggerFactory;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AiSchedulePersistenceService {

    private static final Logger log = LoggerFactory.getLogger(AiSchedulePersistenceService.class);
    private final TaskRepository taskRepository;
    private final EventRepository eventRepository;
    private final MaterialRepository materialRepository;

    @Transactional
    public void persist(String userId, UUID courseId, UUID materialId, List<ScheduledPart> parts, boolean isIncremental) {
        List<Task> tasks = new ArrayList<>();
        List<Event> events = new ArrayList<>();

        for (ScheduledPart part : parts) {
            Task task = Task.builder()
                    .userId(userId)
                    .courseId(courseId)
                    // The part's own link wins: a reschedule re-plans tasks from
                    // several materials at once and passes no single materialId.
                    .materialId(part.materialId() != null ? part.materialId() : materialId)
                    .title(part.title())
                    .durationMinutes(part.minutes())
                    .priority(part.priority() != null ? part.priority() : Priority.MEDIUM)
                    .completed(false)
                    .scheduledDate(part.date())
                    .sequenceOrder(part.sequence())
                    .description(part.description())
                    .coveredSections(part.coveredSections() != null ? String.join(",", part.coveredSections()) : null)
                    .splitPart(part.splitPart())
                    .totalParts(part.totalParts())
                    .locked(isIncremental)
                    .missed(false)
                    .build();
            tasks.add(task);
        }

        List<Task> savedTasks = taskRepository.saveAll(tasks);

        for (int i = 0; i < savedTasks.size(); i++) {
            Task task = savedTasks.get(i);
            ScheduledPart part = parts.get(i);
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
            material.setStatus(MaterialStatus.READY);
            materialRepository.save(material);
        }

        log.info("Persisted {} tasks and {} events for material {} (incremental={})",
                savedTasks.size(), events.size(), materialId, isIncremental);
    }

    @Transactional
    public void fullReschedule(String userId, UUID courseId) {
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
}
