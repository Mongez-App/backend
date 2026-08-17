package com.smartstudy.planning.service;

import com.smartstudy.planning.ai.model.*;
import com.smartstudy.planning.ai.tool.CalendarQuerierTool;
import com.smartstudy.planning.ai.tool.MissedTaskDetectorTool;
import com.smartstudy.planning.ai.tool.PdfExtractorTool;
import com.smartstudy.planning.ai.tool.SchedulerEngineTool;
import com.smartstudy.planning.dto.response.AlertResponse;
import com.smartstudy.planning.model.Course;
import com.smartstudy.planning.model.Priority;
import com.smartstudy.planning.model.Task;
import com.smartstudy.planning.repository.CourseRepository;
import com.smartstudy.planning.repository.TaskRepository;
import com.smartstudy.shared.logging.LoggerFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StudyPlannerAgent {

    private static final Logger log = LoggerFactory.getLogger(StudyPlannerAgent.class);
    private final PdfExtractorTool pdfExtractorTool;
    private final CalendarQuerierTool calendarQuerierTool;
    private final SchedulerEngineTool schedulerEngineTool;
    private final MissedTaskDetectorTool missedTaskDetectorTool;
    private final AiSchedulePersistenceService aiSchedulePersistenceService;
    private final TaskRepository taskRepository;
    private final CourseRepository courseRepository;
    private final TaskPriorityService taskPriorityService;
    private final ChatClient.Builder chatClientBuilder;
    private final ObjectMapper objectMapper;

    private static final int MIN_TASK_MINUTES = 15;

    public AgentPlanResult generatePlan(String userId, UUID courseId, UUID materialId,
                                        int dailyStudyMinutes, String preferredDays, boolean isIncremental) {
        try {
            String rawText = pdfExtractorTool.extract(materialId.toString());
            List<ExtractedTask> tasks = extractTasksFromText(rawText, materialId);
            tasks = applyLowerBoundAndMerge(tasks);

            courseRepository.findByIdAndUserId(courseId, userId)
                    .orElseThrow(() -> new ResponseStatusException(
                            org.springframework.http.HttpStatus.NOT_FOUND, "COURSE_NOT_FOUND"));

            List<AvailableSlot> slots = calendarQuerierTool.query(
                    userId, courseId.toString(), dailyStudyMinutes, preferredDays);

            ScheduleResult scheduleResult = schedulerEngineTool.schedule(tasks, slots);
            List<ScheduledPart> prioritizedParts = prioritizeScheduledParts(userId, courseId, scheduleResult.scheduledParts());

            if (scheduleResult.overCapacity()) {
                log.warn("Over capacity for material {}: {} tasks unscheduled out of {}",
                        materialId, scheduleResult.unscheduledTasks().size(), tasks.size());
                return new AgentPlanResult("over_capacity", new AlertResponse(
                        "Some study tasks could not be fitted before your exam date. No changes were made \u2014 consider adding more study days or increasing your daily study time."));
            }

            aiSchedulePersistenceService.persist(userId, courseId, materialId, prioritizedParts, isIncremental);
            log.info("Plan generated successfully for material {}: {} parts scheduled", materialId, prioritizedParts.size());
            return new AgentPlanResult("scheduled", new AlertResponse("Study tasks have been scheduled for " + materialId + "."));
        } catch (Exception ex) {
            log.error("Failed to generate plan for material {}: {}", materialId, ex.getMessage(), ex);
            return new AgentPlanResult("error", new AlertResponse("Failed to generate study plan: " + ex.getMessage()));
        }
    }

    @Transactional
    public AgentCheckResult checkAndRescheduleRoadmap(String userId,
                                                      int dailyStudyMinutes, String preferredDays) {
        try {
            List<Course> courses = courseRepository.findByUserIdOrderByCreatedAtAsc(userId);
            if (courses.isEmpty()) {
                return new AgentCheckResult("ok", new AlertResponse("No courses found in your roadmap."));
            }

            int totalRescheduled = 0;
            int totalUnscheduled = 0;

            for (Course course : courses) {
                MissedTaskSummary summary = missedTaskDetectorTool.detect(userId, course.getId());

                List<Task> incompleteTasks = taskRepository.findByUserIdAndCourseIdAndCompletedFalse(userId, course.getId());
                if (incompleteTasks.isEmpty()) {
                    continue;
                }

                log.info("Performing full reschedule for course {} ({} missed tasks)", course.getId(), summary.missedCount());
                ScheduleResult scheduleResult = buildReschedule(userId, course.getId(), incompleteTasks,
                        dailyStudyMinutes, preferredDays);
                List<ScheduledPart> prioritizedParts = prioritizeScheduledParts(userId, course.getId(),
                        scheduleResult.scheduledParts());

                if (scheduleResult.overCapacity()) {
                    log.warn("Over capacity after reschedule for course {}: {} unscheduled", course.getId(), scheduleResult.unscheduledTasks().size());
                    totalUnscheduled += scheduleResult.unscheduledTasks().size();
                }

                aiSchedulePersistenceService.replaceTasks(userId, course.getId(), incompleteTasks, prioritizedParts);
                totalRescheduled += prioritizedParts.size();
            }

            if (totalRescheduled > 0) {
                String message = totalRescheduled + " study tasks were rescheduled across your roadmap.";
                if (totalUnscheduled > 0) {
                    message += " " + totalUnscheduled + " tasks could not fit in your available study time; increase your daily minutes, add study days, or adjust deadlines.";
                }
                return new AgentCheckResult(totalUnscheduled > 0 ? "partial_reschedule" : "rescheduled",
                        new AlertResponse(message));
            }
            if (totalUnscheduled > 0) {
                return new AgentCheckResult("partial_reschedule", new AlertResponse(
                        totalUnscheduled + " tasks could not fit in your available study time; increase your daily minutes, add study days, or adjust deadlines."));
            }

            return new AgentCheckResult("ok", null);
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Failed to check roadmap schedule: {}", ex.getMessage(), ex);
            return new AgentCheckResult("error", new AlertResponse("Roadmap schedule check failed: " + ex.getMessage()));
        }
    }

    public ScheduleResult rescheduleCourseTasks(String userId, UUID courseId, List<Task> tasks,
                                                int dailyStudyMinutes, String preferredDays) {
        return rescheduleCourseTasks(userId, courseId, tasks, dailyStudyMinutes, preferredDays, null);
    }

    public ScheduleResult rescheduleCourseTasks(String userId, UUID courseId, List<Task> tasks,
                                                int dailyStudyMinutes, String preferredDays,
                                                LocalDate deadlineExclusive) {
        ScheduleResult scheduleResult = buildReschedule(userId, courseId, tasks, dailyStudyMinutes, preferredDays,
                deadlineExclusive);
        List<ScheduledPart> prioritizedParts = prioritizeScheduledParts(userId, courseId, scheduleResult.scheduledParts());
        ScheduleResult prioritizedResult = new ScheduleResult(prioritizedParts,
                scheduleResult.unscheduledTasks(), scheduleResult.overCapacity());
        aiSchedulePersistenceService.replaceTasks(userId, courseId, tasks, prioritizedParts);
        return prioritizedResult;
    }

    private ScheduleResult buildReschedule(String userId, UUID courseId, List<Task> tasks,
                                           int dailyStudyMinutes, String preferredDays) {
        return buildReschedule(userId, courseId, tasks, dailyStudyMinutes, preferredDays, null);
    }

    private ScheduleResult buildReschedule(String userId, UUID courseId, List<Task> tasks,
                                           int dailyStudyMinutes, String preferredDays,
                                           LocalDate deadlineExclusive) {
        List<ExtractedTask> remainingTasks = tasks.stream()
                .sorted(Comparator
                        .comparing((Task task) -> task.getScheduledDate() != null ? task.getScheduledDate() : LocalDate.MAX)
                        .thenComparing(task -> task.getSequenceOrder() != null ? task.getSequenceOrder() : 0)
                        .thenComparing(Task::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(this::toExtractedTaskForReschedule)
                .toList();

        List<AvailableSlot> slots = calendarQuerierTool.query(
                userId,
                courseId.toString(),
                dailyStudyMinutes,
                preferredDays,
                deadlineExclusive,
                tasks.stream().map(Task::getId).toList());
        return schedulerEngineTool.schedule(remainingTasks, slots);
    }

    private ExtractedTask toExtractedTaskForReschedule(Task task) {
        return new ExtractedTask(
                task.getTitle(),
                task.getDurationMinutes(),
                task.getSequenceOrder() != null ? task.getSequenceOrder() : 0,
                task.getDescription(),
                task.getCoveredSections() != null && !task.getCoveredSections().isBlank()
                        ? List.of(task.getCoveredSections().split(","))
                        : List.of(),
                task.getPriority() != null ? task.getPriority() : Priority.MEDIUM,
                task.getMaterialId()
        );
    }

    private List<ScheduledPart> prioritizeScheduledParts(String userId, UUID courseId, List<ScheduledPart> parts) {
        return parts.stream()
                .map(part -> {
                    Priority priority = taskPriorityService.determinePriority(userId, courseId, part.date());
                    return new ScheduledPart(
                            part.title(),
                            part.date(),
                            part.minutes(),
                            part.sequence(),
                            part.splitPart(),
                            part.totalParts(),
                            part.description(),
                            part.coveredSections(),
                            priority,
                            part.materialId()
                    );
                })
                .toList();
    }

    private List<ExtractedTask> extractTasksFromText(String rawText, UUID materialId) {
        String systemPrompt = """
                You are a study planning assistant focused exclusively on the provided course material.
                Analyze the raw PDF text and create study tasks based ONLY on content explicitly present in the material.
                Do NOT use external knowledge or generic assumptions about how long a topic usually takes.

                For each distinct section or topic identified in the material, create one focused study task.
                Estimate duration based strictly on the content depth and volume found within that section.

                Return ONLY a valid JSON array of objects with fields:
                - title (String)
                - estimatedMinutes (int) — minimum 15 minutes per task
                - sequenceOrder (int) — strict study order starting from 1
                - description (String) — brief description of what the task covers
                - coveredSections (String[]) — section names/topics covered by this task

                Do not include markdown fences. Example:
                [{"title":"Operating Systems","estimatedMinutes":15,"sequenceOrder":1,"description":"Covers process management and memory allocation","coveredSections":["Operating Systems"]}]

                Tasks must be in strict study order. Do not skip or compress sections.
                """;

        try {
            String prompt = "Extract study tasks from the following material text:\n\n" + rawText;

            String response = chatClientBuilder.build().prompt()
                    .system(systemPrompt)
                    .user(prompt)
                    .call()
                    .content();

            response = response.trim();
            if (response.startsWith("```json")) {
                response = response.substring(7);
            } else if (response.startsWith("```")) {
                response = response.substring(3);
            }
            if (response.endsWith("```")) {
                response = response.substring(0, response.length() - 3);
            }
            response = response.trim();

            List<ExtractedTask> tasks = objectMapper.readValue(response,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, ExtractedTask.class));

            if (tasks.isEmpty()) {
                throw new IllegalStateException("LLM returned empty task list for material " + materialId);
            }

            tasks.sort((a, b) -> Integer.compare(a.sequenceOrder(), b.sequenceOrder()));
            // The LLM has no idea which material it read, so stamp the link here
            // and let every later rewrite of the task carry it along.
            return tasks.stream().map(task -> task.withMaterialId(materialId)).toList();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to extract tasks from PDF text: " + ex.getMessage(), ex);
        }
    }

    private List<ExtractedTask> applyLowerBoundAndMerge(List<ExtractedTask> tasks) {
        if (tasks.isEmpty()) return tasks;
        List<ExtractedTask> result = new ArrayList<>();
        List<ExtractedTask> buffer = new ArrayList<>();

        for (ExtractedTask task : tasks) {
            if (task.estimatedMinutes() < MIN_TASK_MINUTES) {
                buffer.add(task);
            } else {
                flushBuffer(result, buffer);
                result.add(task);
                buffer.clear();
            }
        }
        flushBuffer(result, buffer);
        return result;
    }

    private void flushBuffer(List<ExtractedTask> result, List<ExtractedTask> buffer) {
        if (buffer.isEmpty()) return;
        if (buffer.size() == 1) {
            ExtractedTask t = buffer.get(0);
            result.add(new ExtractedTask(
                    t.title(), MIN_TASK_MINUTES, t.sequenceOrder(),
                    t.description(), t.coveredSections(), t.priority(), t.materialId()));
        } else {
            result.add(mergeGroup(buffer));
        }
    }

    private ExtractedTask mergeGroup(List<ExtractedTask> group) {
        String combinedTitle = group.stream()
                .map(ExtractedTask::title)
                .reduce((a, b) -> a + " | " + b)
                .orElse("Merged Tasks");
        String combinedDescription = group.stream()
                .map(ExtractedTask::description)
                .filter(d -> d != null && !d.isBlank())
                .reduce((a, b) -> a + " | " + b)
                .orElse(null);
        List<String> combinedSections = group.stream()
                .flatMap(t -> (t.coveredSections() == null ? List.<String>of() : t.coveredSections()).stream())
                .distinct()
                .toList();
        int totalMinutes = group.stream().mapToInt(ExtractedTask::estimatedMinutes).sum();
        int firstSequence = group.get(0).sequenceOrder();
        Priority firstPriority = group.get(0).priority() != null ? group.get(0).priority() : Priority.MEDIUM;

        // A merge group is always consecutive tasks from one material.
        return new ExtractedTask(combinedTitle, totalMinutes, firstSequence, combinedDescription,
                combinedSections, firstPriority, group.get(0).materialId());
    }
}
