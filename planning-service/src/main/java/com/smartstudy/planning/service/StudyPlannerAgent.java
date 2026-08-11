package com.smartstudy.planning.service;

import com.smartstudy.planning.ai.model.*;
import com.smartstudy.planning.ai.tool.CalendarQuerierTool;
import com.smartstudy.planning.ai.tool.MissedTaskDetectorTool;
import com.smartstudy.planning.ai.tool.PdfExtractorTool;
import com.smartstudy.planning.ai.tool.SchedulerEngineTool;
import com.smartstudy.planning.dto.response.AlertResponse;
import com.smartstudy.planning.model.Course;
import com.smartstudy.planning.model.Event;
import com.smartstudy.planning.model.EventType;
import com.smartstudy.planning.model.Priority;
import com.smartstudy.planning.model.Task;
import com.smartstudy.planning.repository.CourseRepository;
import com.smartstudy.planning.repository.EventRepository;
import com.smartstudy.planning.repository.TaskRepository;
import com.smartstudy.shared.logging.LoggerFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
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
    private final EventRepository eventRepository;
    private final ChatClient.Builder chatClientBuilder;
    private final ObjectMapper objectMapper;

    private static final int EXAM_HIGH_DAYS = 7;
    private static final int EXAM_MEDIUM_DAYS = 14;
    private static final int EVENT_SEARCH_WINDOW_DAYS = 14;
    private static final int SCORE_LOW = 33;
    private static final int SCORE_MEDIUM = 50;
    private static final int SCORE_HIGH = 100;
    private static final int MIN_TASK_MINUTES = 15;

    public AgentPlanResult generatePlan(String userId, UUID courseId, UUID materialId,
                                        int dailyStudyMinutes, String preferredDays, boolean isIncremental) {
        try {
            String rawText = pdfExtractorTool.extract(materialId.toString());
            List<ExtractedTask> tasks = extractTasksFromText(rawText, materialId);
            tasks = applyLowerBoundAndMerge(tasks);

            Course course = courseRepository.findByIdAndUserId(courseId, userId)
                    .orElseThrow(() -> new ResponseStatusException(
                            org.springframework.http.HttpStatus.NOT_FOUND, "COURSE_NOT_FOUND"));
            LocalDate examDate = course.getExamDate() != null
                    ? course.getExamDate().atZone(java.time.ZoneOffset.UTC).toLocalDate()
                    : LocalDate.now().plusYears(1);
            assignPriorities(userId, courseId, tasks, materialId, examDate);

            List<AvailableSlot> slots = calendarQuerierTool.query(
                    userId, courseId.toString(), dailyStudyMinutes, preferredDays);

            ScheduleResult scheduleResult = schedulerEngineTool.schedule(tasks, slots);

            if (scheduleResult.overCapacity()) {
                log.warn("Over capacity for material {}: {} tasks unscheduled out of {}",
                        materialId, scheduleResult.unscheduledTasks().size(), tasks.size());
                return new AgentPlanResult("over_capacity", new AlertResponse(
                        "Some study tasks could not be fitted before your exam date. No changes were made \u2014 consider adding more study days or increasing your daily study time."));
            }

            aiSchedulePersistenceService.persist(userId, courseId, materialId, scheduleResult.scheduledParts(), isIncremental);
            log.info("Plan generated successfully for material {}: {} parts scheduled", materialId, scheduleResult.scheduledParts().size());
            return new AgentPlanResult("scheduled", new AlertResponse("Study tasks have been scheduled for " + materialId + "."));
        } catch (Exception ex) {
            log.error("Failed to generate plan for material {}: {}", materialId, ex.getMessage(), ex);
            return new AgentPlanResult("error", new AlertResponse("Failed to generate study plan: " + ex.getMessage()));
        }
    }

    private void assignPriorities(String userId, UUID courseId, List<ExtractedTask> tasks, UUID materialId, LocalDate examDate) {
        LocalDate today = LocalDate.now();
        long daysToExam = ChronoUnit.DAYS.between(today, examDate);
        int size = tasks.size();
        if (size == 0) return;

        int examScore = resolveExamProximityScore(daysToExam);

        for (int i = 0; i < size; i++) {
            ExtractedTask task = tasks.get(i);
            LocalDate taskDate = today.plusDays(i);
            Event nearestEvent = findNearestEvent(userId, courseId, taskDate, today);
            int eventScore = resolveEventTypeScore(nearestEvent);
            int finalScore = Math.max(examScore, eventScore);
            Priority priority = scoreToPriority(finalScore);

            tasks.set(i, new ExtractedTask(
                    task.title(),
                    task.estimatedMinutes(),
                    task.sequenceOrder(),
                    task.description(),
                    task.coveredSections(),
                    priority
            ));
        }
    }

    private Event findNearestEvent(String userId, UUID courseId, LocalDate date, LocalDate today) {
        if (userId == null) {
            return null;
        }
        Instant windowStart = date.minusDays(3).atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant windowEnd = date.plusDays(EVENT_SEARCH_WINDOW_DAYS)
                .atStartOfDay(ZoneOffset.UTC).toInstant();

        List<Event> events = courseId != null
                ? eventRepository.findByUserIdAndCourseIdAndStartDateBetween(userId, courseId, windowStart, windowEnd)
                : eventRepository.findByUserIdAndStartDateBetween(userId, windowStart, windowEnd);
        if (events.isEmpty()) {
            return null;
        }
        return events.stream()
                .min(Comparator.comparing(e -> Math.abs(ChronoUnit.DAYS.between(date,
                        e.getStartDate().atZone(ZoneOffset.UTC).toLocalDate()))))
                .orElse(null);
    }

    private int resolveExamProximityScore(long daysToExam) {
        if (daysToExam <= EXAM_HIGH_DAYS) {
            return SCORE_HIGH;
        } else if (daysToExam <= EXAM_MEDIUM_DAYS) {
            return SCORE_MEDIUM;
        } else {
            return SCORE_LOW;
        }
    }

    private int resolveEventTypeScore(Event event) {
        if (event == null || event.getEventType() == null) {
            return SCORE_LOW;
        }
        Optional<EventType> eventType = EventType.fromWireValue(event.getEventType());
        if (eventType.isEmpty()) {
            return SCORE_LOW;
        }
        return switch (eventType.get()) {
            case EXAM -> SCORE_HIGH;
            case ASSIGNMENT, QUIZ, PROJECT, MIDTERM -> SCORE_MEDIUM;
        };
    }

    private Priority scoreToPriority(int score) {
        if (score < 34) {
            return Priority.LOW;
        } else if (score < 67) {
            return Priority.MEDIUM;
        } else {
            return Priority.HIGH;
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

            for (Course course : courses) {
                MissedTaskSummary summary = missedTaskDetectorTool.detect(userId, course.getId());

                if (summary.missedCount() == 0 || !summary.requiresFullReschedule()) {
                    continue;
                }

                log.info("Performing full reschedule for course {} ({} missed tasks)", course.getId(), summary.missedCount());
                aiSchedulePersistenceService.fullReschedule(userId, course.getId());

                List<ExtractedTask> remainingTasks = taskRepository.findByUserIdAndCourseIdAndCompletedFalse(userId, course.getId())
                        .stream()
                        .sorted(Comparator
                                .comparing((Task t) -> t.getPriority() != null ? t.getPriority().ordinal() : 0).reversed()
                                .thenComparingInt(t -> t.getSequenceOrder() != null ? t.getSequenceOrder() : 0))
                        .map(t -> new ExtractedTask(t.getTitle(), t.getDurationMinutes(),
                                t.getSequenceOrder() != null ? t.getSequenceOrder() : 0,
                                t.getDescription(), t.getCoveredSections() != null ? List.of(t.getCoveredSections().split(",")) : List.of(),
                                t.getPriority()))
                        .toList();

                List<com.smartstudy.planning.ai.model.AvailableSlot> slots = calendarQuerierTool.query(
                        userId, course.getId().toString(), dailyStudyMinutes, preferredDays);

                ScheduleResult scheduleResult = schedulerEngineTool.schedule(remainingTasks, slots);

                if (scheduleResult.overCapacity()) {
                    log.warn("Over capacity after reschedule for course {}: {} unscheduled", course.getId(), scheduleResult.unscheduledTasks().size());
                    continue;
                }

                aiSchedulePersistenceService.persist(userId, course.getId(), null, scheduleResult.scheduledParts(), false);
                totalRescheduled += summary.missedCount();
            }

            if (totalRescheduled > 0) {
                return new AgentCheckResult("rescheduled", new AlertResponse(
                        totalRescheduled + " missed tasks were rescheduled across your roadmap."));
            }

            return new AgentCheckResult("ok", null);
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Failed to check roadmap schedule: {}", ex.getMessage(), ex);
            return new AgentCheckResult("error", new AlertResponse("Roadmap schedule check failed: " + ex.getMessage()));
        }
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
            return tasks;
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
                    t.description(), t.coveredSections(), t.priority()));
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

        return new ExtractedTask(combinedTitle, totalMinutes, firstSequence, combinedDescription, combinedSections, firstPriority);
    }
}

