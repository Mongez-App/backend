package com.smartstudy.planning.service;

import com.smartstudy.planning.ai.model.*;
import com.smartstudy.planning.ai.tool.CalendarQuerierTool;
import com.smartstudy.planning.ai.tool.MissedTaskDetectorTool;
import com.smartstudy.planning.ai.tool.PdfExtractorTool;
import com.smartstudy.planning.ai.tool.SchedulerEngineTool;
import com.smartstudy.planning.dto.response.AlertResponse;
import com.smartstudy.planning.model.Task;
import com.smartstudy.planning.repository.TaskRepository;
import com.smartstudy.shared.logging.LoggerFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

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
    private final ChatClient.Builder chatClientBuilder;
    private final ObjectMapper objectMapper;

    public AgentPlanResult generatePlan(String userId, UUID courseId, UUID materialId,
                                        int dailyStudyMinutes, String preferredDays, boolean isIncremental) {
        try {
            String rawText = pdfExtractorTool.extract(materialId.toString());
            List<ExtractedTask> tasks = extractTasksFromText(rawText, materialId);

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

    @Transactional
    public AgentCheckResult checkAndReschedule(String userId, UUID courseId,
                                               int dailyStudyMinutes, String preferredDays) {
        try {
            MissedTaskSummary summary = missedTaskDetectorTool.detect(userId, courseId);

            if (summary.missedCount() == 0) {
                return new AgentCheckResult("ok", null);
            }

            if (!summary.requiresFullReschedule()) {
                return new AgentCheckResult("ok", new AlertResponse(summary.missedCount() + " missed tasks detected."));
            }

            log.info("Performing full reschedule for course {} ({} missed tasks)", courseId, summary.missedCount());
            aiSchedulePersistenceService.fullReschedule(userId, courseId);

            List<ExtractedTask> remainingTasks = taskRepository.findByUserIdAndCourseIdAndCompletedFalse(userId, courseId)
                    .stream()
                    .sorted(Comparator
                            .comparing((Task t) -> t.getPriority() != null ? t.getPriority().ordinal() : 0).reversed()
                            .thenComparingInt(t -> t.getSequenceOrder() != null ? t.getSequenceOrder() : 0))
                    .map(t -> new ExtractedTask(t.getTitle(), t.getDurationMinutes(),
                            t.getSequenceOrder() != null ? t.getSequenceOrder() : 0, null))
                    .toList();

            List<com.smartstudy.planning.ai.model.AvailableSlot> slots = calendarQuerierTool.query(
                    userId, courseId.toString(), dailyStudyMinutes, preferredDays);

            ScheduleResult scheduleResult = schedulerEngineTool.schedule(remainingTasks, slots);

            if (scheduleResult.overCapacity()) {
                log.warn("Over capacity after reschedule for course {}: {} unscheduled", courseId, scheduleResult.unscheduledTasks().size());
                return new AgentCheckResult("ok", new AlertResponse(
                        "Some missed tasks could not be rescheduled before your exam date. No changes were made \u2014 consider adding more study days or increasing your daily study time."));
            }

            aiSchedulePersistenceService.persist(userId, courseId, null, scheduleResult.scheduledParts(), false);
            return new AgentCheckResult("rescheduled", new AlertResponse(
                    summary.missedCount() + " missed tasks were rescheduled around your available study days."));
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Failed to check schedule for course {}: {}", courseId, ex.getMessage(), ex);
            return new AgentCheckResult("error", new AlertResponse("Schedule check failed: " + ex.getMessage()));
        }
    }

    private List<ExtractedTask> extractTasksFromText(String rawText, UUID materialId) {
        String systemPrompt = """
                You are a study planning assistant. Given raw PDF text from a course material, extract a list of ordered study tasks.
                Return ONLY a valid JSON array of objects with fields: title (String), estimatedMinutes (int), sequenceOrder (int), notes (String nullable, default null).
                Do not include any markdown fences. Example: [{"title":"Chapter 1","estimatedMinutes":45,"sequenceOrder":1,"notes":null}]
                Tasks must be in strict study order. Do not compress or skip sections.
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
}

