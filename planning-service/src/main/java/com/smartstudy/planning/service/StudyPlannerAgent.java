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
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StudyPlannerAgent {

    private static final Logger log = LoggerFactory.getLogger(StudyPlannerAgent.class);
    private static final int MAX_PLAN_ITERATIONS = 10;
    private static final int MAX_RESCHEDULE_ITERATIONS = 15;
    private static final int MAX_CONSECUTIVE_MALFORMED = 3;

    private static final String PLAN_SYSTEM_PROMPT = """
            You are an autonomous study planner agent. Your goal is to generate a study schedule for a user's uploaded course material.

            You must respond with a single JSON object matching this schema:
            {"action":"call_tool"|"finish","tool":"tool_name","arguments":{"key":"value"}}

            For finish action, include status and message in arguments: {"action":"finish","tool":"finish","arguments":{"status":"scheduled","message":"..."}}

            Available tools:
            1. extract_and_parse_tasks(materialId: String) - Extract and parse study tasks from the uploaded PDF. Populates state.tasks. MUST be called first.
            2. assign_priorities() - Assign HIGH/MEDIUM/LOW priorities to state.tasks based on position and exam date. Call immediately after extraction.
            3. query_available_slots() - Query available study slots between now and the exam date based on user preferences. Populates state.slots.
            4. schedule_tasks() - Bin-pack state.tasks into state.slots. Populates state.scheduleResult.
            5. persist_schedule() - Save state.scheduleResult to database and update material status. Call only after successful scheduling.
            6. finish(status: String, message: String) - Complete the planning. Status is "scheduled", "over_capacity", or "error".

            Rules:
            - Call extract_and_parse_tasks first if tasks are not yet extracted.
            - Call assign_priorities after extraction and before scheduling.
            - Call query_available_slots before schedule_tasks.
            - Call schedule_tasks when tasks (with priorities) and slots are both available.
            - Call persist_schedule after schedule_tasks succeeds (overCapacity=false).
            - Call finish with status "scheduled" after persist_schedule completes successfully.
            - Call finish with status "over_capacity" if schedule_tasks returns unscheduled tasks.
            - Do not call tools that have already completed successfully.
            - Maximum %d iterations.
            """;

    private static final String RESCHEDULE_SYSTEM_PROMPT = """
            You are an autonomous study roadmap rescheduler agent. Your goal is to detect missed tasks and reschedule them for a course.

            You must respond with a single JSON object matching this schema:
            {"action":"call_tool"|"finish","tool":"tool_name","arguments":{"key":"value"}}

            For finish action, include status and message in arguments: {"action":"finish","tool":"finish","arguments":{"status":"rescheduled","message":"..."}}

            Available tools:
            1. detect_missed_tasks(userId: String, courseId: String) - Detect overdue incomplete tasks. Populates state.missedSummary.
            2. full_reschedule(userId: String, courseId: String) - Delete future tasks and events for this course.
            3. load_remaining_tasks(userId: String, courseId: String) - Load incomplete tasks from database. Populates state.tasks.
            4. assign_priorities() - Assign HIGH/MEDIUM/LOW priorities to state.tasks.
            5. query_available_slots() - Query available study slots. Populates state.slots.
            6. schedule_tasks() - Bin-pack state.tasks into state.slots. Populates state.scheduleResult.
            7. persist_schedule() - Save state.scheduleResult to database.
            8. finish(status: String, message: String) - Complete the rescheduling.

            Rules:
            - Call detect_missed_tasks first.
            - If no missed tasks or full reschedule is not required, call finish with status "ok".
            - If full reschedule is required, call full_reschedule before load_remaining_tasks.
            - Call load_remaining_tasks after full_reschedule.
            - Call assign_priorities after load_remaining_tasks.
            - Call query_available_slots before schedule_tasks.
            - Call schedule_tasks when tasks and slots are available.
            - Call persist_schedule after successful scheduling.
            - Call finish with status "rescheduled" when done.
            - Maximum %d iterations.
            """;

    private final PdfExtractorTool pdfExtractorTool;
    private final CalendarQuerierTool calendarQuerierTool;
    private final SchedulerEngineTool schedulerEngineTool;
    private final MissedTaskDetectorTool missedTaskDetectorTool;
    private final AiSchedulePersistenceService aiSchedulePersistenceService;
    private final TaskRepository taskRepository;
    private final CourseRepository courseRepository;
    private final ChatClient.Builder chatClientBuilder;
    private final ObjectMapper objectMapper;

    public AgentPlanResult generatePlan(String userId, UUID courseId, UUID materialId,
                                        int dailyStudyMinutes, String preferredDays, boolean isIncremental) {
        try {
            Course course = courseRepository.findByIdAndUserId(courseId, userId)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND, "COURSE_NOT_FOUND"));
            LocalDate examDate = course.getExamDate() != null
                    ? course.getExamDate().atZone(java.time.ZoneOffset.UTC).toLocalDate()
                    : LocalDate.now().plusYears(1);

            AgentState state = new AgentState(userId, courseId, materialId, dailyStudyMinutes, preferredDays, isIncremental);
            state.examDate = examDate;

            String systemPrompt = String.format(PLAN_SYSTEM_PROMPT, MAX_PLAN_ITERATIONS);
            String userPrompt = buildPlanUserPrompt(state);

            for (int i = 0; i < MAX_PLAN_ITERATIONS; i++) {
                state.iteration = i + 1;

                AgentDecision decision = callLLMForDecision(systemPrompt, userPrompt);
                if (decision == null) {
                    state.consecutiveMalformedDecisions++;
                    if (state.consecutiveMalformedDecisions >= MAX_CONSECUTIVE_MALFORMED) {
                        return new AgentPlanResult("error", new AlertResponse("Agent failed: too many malformed decisions."));
                    }
                    userPrompt = "Your previous response was not valid JSON. Respond with a valid AgentDecision JSON object.";
                    continue;
                }

                state.consecutiveMalformedDecisions = 0;

                String result = executePlanDecision(decision, state);
                if (result == null) {
                    String status = decision.arguments().getOrDefault("status", "error");
                    String message = decision.arguments().getOrDefault("message", "Unknown error");
                    if ("scheduled".equals(status)) {
                        return new AgentPlanResult("scheduled", new AlertResponse(message));
                    } else if ("over_capacity".equals(status)) {
                        return new AgentPlanResult("over_capacity", new AlertResponse(message));
                    } else {
                        return new AgentPlanResult("error", new AlertResponse(message));
                    }
                }

                userPrompt = buildPlanUserPrompt(state);
            }

            return new AgentPlanResult("error", new AlertResponse("Agent failed: maximum iterations reached."));
        } catch (ResponseStatusException ex) {
            throw ex;
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
            StringBuilder alertMessage = new StringBuilder();

            for (Course course : courses) {
                MissedTaskSummary summary = missedTaskDetectorTool.detect(userId, course.getId());

                if (summary.missedCount() == 0 || !summary.requiresFullReschedule()) {
                    continue;
                }

                log.info("Performing agentic reschedule for course {} ({} missed tasks)", course.getId(), summary.missedCount());

                AgentState state = new AgentState(userId, course.getId(), null, dailyStudyMinutes, preferredDays, false);
                state.examDate = course.getExamDate() != null
                        ? course.getExamDate().atZone(java.time.ZoneOffset.UTC).toLocalDate()
                        : LocalDate.now().plusYears(1);
                state.log("Detected " + summary.missedCount() + " missed tasks for course " + course.getId());

                String systemPrompt = String.format(RESCHEDULE_SYSTEM_PROMPT, MAX_RESCHEDULE_ITERATIONS);
                String userPrompt = buildRescheduleUserPrompt(state);

                boolean finished = false;
                for (int i = 0; i < MAX_RESCHEDULE_ITERATIONS; i++) {
                    state.iteration = i + 1;

                    AgentDecision decision = callLLMForDecision(systemPrompt, userPrompt);
                    if (decision == null) {
                        state.consecutiveMalformedDecisions++;
                        if (state.consecutiveMalformedDecisions >= MAX_CONSECUTIVE_MALFORMED) {
                            log.error("Reschedule agent failed for course {}: too many malformed decisions", course.getId());
                            break;
                        }
                        userPrompt = "Your previous response was not valid JSON. Respond with a valid AgentDecision JSON object.";
                        continue;
                    }

                    state.consecutiveMalformedDecisions = 0;
                    String result = executeRescheduleDecision(decision, state, userId, course.getId());

                    if (result == null) {
                        String status = decision.arguments().getOrDefault("status", "error");
                        if ("rescheduled".equals(status)) {
                            totalRescheduled += summary.missedCount();
                            alertMessage.append(summary.missedCount()).append(" tasks rescheduled for ").append(course.getName()).append(". ");
                        }
                        finished = true;
                        break;
                    }

                    userPrompt = buildRescheduleUserPrompt(state);
                }

                if (!finished) {
                    log.warn("Reschedule agent did not finish for course {}", course.getId());
                }
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

    AgentDecision callLLMForDecision(String systemPrompt, String userPrompt) {
        try {
            String response = chatClientBuilder.build().prompt()
                    .system(systemPrompt)
                    .user(userPrompt)
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

            return objectMapper.readValue(response, AgentDecision.class);
        } catch (Exception ex) {
            log.warn("Failed to parse LLM decision: {}", ex.getMessage());
            return null;
        }
    }

    String executePlanDecision(AgentDecision decision, AgentState state) {
        if ("finish".equals(decision.action())) {
            return null;
        }

        if (!"call_tool".equals(decision.action())) {
            state.log("Invalid action: " + decision.action());
            return "Invalid action '" + decision.action() + "'. Use 'call_tool' or 'finish'.";
        }

        String tool = decision.tool();
        if (tool == null || tool.isBlank()) {
            state.log("Missing tool name");
            return "Missing tool name in decision.";
        }

        return switch (tool) {
            case "extract_and_parse_tasks" -> executeExtractAndParseTasks(state);
            case "assign_priorities" -> executeAssignPriorities(state);
            case "query_available_slots" -> executeQueryAvailableSlots(state);
            case "schedule_tasks" -> executeScheduleTasks(state);
            case "persist_schedule" -> executePersistSchedule(state);
            default -> {
                state.log("Unknown tool: " + tool);
                yield "Unknown tool: " + tool;
            }
        };
    }

    String executeRescheduleDecision(AgentDecision decision, AgentState state, String userId, UUID courseId) {
        if ("finish".equals(decision.action())) {
            return null;
        }

        if (!"call_tool".equals(decision.action())) {
            state.log("Invalid action: " + decision.action());
            return "Invalid action '" + decision.action() + "'. Use 'call_tool' or 'finish'.";
        }

        String tool = decision.tool();
        if (tool == null || tool.isBlank()) {
            state.log("Missing tool name");
            return "Missing tool name in decision.";
        }

        return switch (tool) {
            case "detect_missed_tasks" -> executeDetectMissedTasks(state, userId, courseId);
            case "full_reschedule" -> executeFullReschedule(state, userId, courseId);
            case "load_remaining_tasks" -> executeLoadRemainingTasks(state, userId, courseId);
            case "assign_priorities" -> executeAssignPriorities(state);
            case "query_available_slots" -> executeQueryAvailableSlots(state);
            case "schedule_tasks" -> executeScheduleTasks(state);
            case "persist_schedule" -> executeReschedulePersist(state);
            default -> {
                state.log("Unknown tool: " + tool);
                yield "Unknown tool: " + tool;
            }
        };
    }

    String executeExtractAndParseTasks(AgentState state) {
        if (state.isTasksExtracted()) {
            return "Tasks already extracted (" + state.tasks.size() + " tasks). Call another tool.";
        }
        try {
            TaskExtractionResult result = pdfExtractorTool.extractAndParseTasks(state.materialId.toString());
            state.tasks = result.tasks();
            assignPriorities(state.tasks, state.examDate);
            state.log("Extracted " + result.tasks().size() + " tasks and assigned priorities");
            return result.summary();
        } catch (Exception ex) {
            state.log("Failed to extract tasks: " + ex.getMessage());
            return "Error extracting tasks: " + ex.getMessage();
        }
    }

    String executeDetectMissedTasks(AgentState state, String userId, UUID courseId) {
        try {
            MissedTaskSummary summary = missedTaskDetectorTool.detect(userId, courseId);
            state.log("Detected " + summary.missedCount() + " missed tasks, requiresFullReschedule=" + summary.requiresFullReschedule());
            if (summary.missedCount() == 0) {
                return "No missed tasks detected. Call finish with status 'ok'.";
            }
            if (!summary.requiresFullReschedule()) {
                return "Missed tasks detected but full reschedule not required. Call finish with status 'ok'.";
            }
            return "Detected " + summary.missedCount() + " missed tasks requiring full reschedule. Next: full_reschedule.";
        } catch (Exception ex) {
            state.log("Failed to detect missed tasks: " + ex.getMessage());
            return "Error detecting missed tasks: " + ex.getMessage();
        }
    }

    String executeFullReschedule(AgentState state, String userId, UUID courseId) {
        try {
            aiSchedulePersistenceService.fullReschedule(userId, courseId);
            state.log("Performed full reschedule for course " + courseId);
            return "Deleted future tasks and events. Next: load_remaining_tasks.";
        } catch (Exception ex) {
            state.log("Failed to full reschedule: " + ex.getMessage());
            return "Error during full reschedule: " + ex.getMessage();
        }
    }

    String executeLoadRemainingTasks(AgentState state, String userId, UUID courseId) {
        if (state.isTasksExtracted()) {
            return "Remaining tasks already loaded (" + state.tasks.size() + " tasks). Call another tool.";
        }
        try {
            List<ExtractedTask> remainingTasks = taskRepository.findByUserIdAndCourseIdAndCompletedFalse(userId, courseId)
                    .stream()
                    .sorted(Comparator
                            .comparing((Task t) -> t.getPriority() != null ? t.getPriority().ordinal() : 0).reversed()
                            .thenComparingInt(t -> t.getSequenceOrder() != null ? t.getSequenceOrder() : 0))
                     .map(t -> new ExtractedTask(t.getTitle(), t.getDurationMinutes(),
                             t.getSequenceOrder() != null ? t.getSequenceOrder() : 0, null, t.getPriority()))
                    .toList();
            state.tasks = remainingTasks;
            state.log("Loaded " + remainingTasks.size() + " remaining tasks from database");
            return "Loaded " + remainingTasks.size() + " remaining tasks. Next: assign_priorities.";
        } catch (Exception ex) {
            state.log("Failed to load remaining tasks: " + ex.getMessage());
            return "Error loading remaining tasks: " + ex.getMessage();
        }
    }

    String executeAssignPriorities(AgentState state) {
        if (!state.isTasksExtracted()) {
            return "No tasks available. Call extract_and_parse_tasks or load_remaining_tasks first.";
        }
        try {
            assignPriorities(state.tasks, state.examDate);
            state.log("Assigned priorities to " + state.tasks.size() + " tasks");
            return "Assigned priorities to " + state.tasks.size() + " tasks.";
        } catch (Exception ex) {
            state.log("Failed to assign priorities: " + ex.getMessage());
            return "Error assigning priorities: " + ex.getMessage();
        }
    }

    String executeQueryAvailableSlots(AgentState state) {
        if (state.areSlotsQueried()) {
            return "Slots already queried (" + state.slots.size() + " slots). Call another tool.";
        }
        try {
            List<AvailableSlot> slots = calendarQuerierTool.query(
                    state.userId, state.courseId.toString(), state.dailyStudyMinutes, state.preferredDays);
            state.slots = slots;
            int totalMinutes = slots.stream().mapToInt(AvailableSlot::availableMinutes).sum();
            state.log("Queried " + slots.size() + " available slots (" + totalMinutes + " min total)");
            return "Queried " + slots.size() + " available slots (" + totalMinutes + " min total).";
        } catch (Exception ex) {
            state.log("Failed to query slots: " + ex.getMessage());
            return "Error querying available slots: " + ex.getMessage();
        }
    }

    String executeScheduleTasks(AgentState state) {
        if (!state.isTasksExtracted() || !state.areSlotsQueried()) {
            return "Cannot schedule: tasks=" + state.isTasksExtracted() + ", slots=" + state.areSlotsQueried() + ". Ensure both are ready.";
        }
        if (state.isScheduled()) {
            return "Tasks already scheduled (" + state.scheduleResult.scheduledParts().size() + " parts). Call another tool.";
        }
        try {
            ScheduleResult result = schedulerEngineTool.schedule(state.tasks, state.slots);
            state.scheduleResult = result;
            state.log("Scheduled " + result.scheduledParts().size() + " parts, " + result.unscheduledTasks().size() + " unscheduled, overCapacity=" + result.overCapacity());
            return "Scheduled " + result.scheduledParts().size() + " task parts, " + result.unscheduledTasks().size() + " unscheduled, overCapacity=" + result.overCapacity() + ".";
        } catch (Exception ex) {
            state.log("Failed to schedule tasks: " + ex.getMessage());
            return "Error scheduling tasks: " + ex.getMessage();
        }
    }

    String executePersistSchedule(AgentState state) {
        if (!state.isScheduled()) {
            return "No schedule to persist. Call schedule_tasks first.";
        }
        if (state.scheduleResult.overCapacity()) {
            return "Cannot persist: schedule is over capacity with " + state.scheduleResult.unscheduledTasks().size() + " unscheduled tasks. Call finish with status 'over_capacity'.";
        }
        try {
            aiSchedulePersistenceService.persist(
                    state.userId, state.courseId, state.materialId,
                    state.scheduleResult.scheduledParts(), state.isIncremental);
            state.log("Persisted " + state.scheduleResult.scheduledParts().size() + " scheduled parts to database");
            return "Persisted " + state.scheduleResult.scheduledParts().size() + " scheduled parts. Call finish with status 'scheduled'.";
        } catch (Exception ex) {
            state.log("Failed to persist schedule: " + ex.getMessage());
            return "Error persisting schedule: " + ex.getMessage();
        }
    }

    String executeReschedulePersist(AgentState state) {
        if (!state.isScheduled()) {
            return "No schedule to persist. Call schedule_tasks first.";
        }
        if (state.scheduleResult.overCapacity()) {
            return "Cannot persist: schedule is over capacity. Call finish with status 'error'.";
        }
        try {
            aiSchedulePersistenceService.persist(
                    state.userId, state.courseId, null,
                    state.scheduleResult.scheduledParts(), false);
            state.log("Persisted " + state.scheduleResult.scheduledParts().size() + " rescheduled parts to database");
            return "Persisted " + state.scheduleResult.scheduledParts().size() + " rescheduled parts. Call finish with status 'rescheduled'.";
        } catch (Exception ex) {
            state.log("Failed to persist reschedule: " + ex.getMessage());
            return "Error persisting reschedule: " + ex.getMessage();
        }
    }

    void assignPriorities(List<ExtractedTask> tasks, LocalDate examDate) {
        LocalDate today = LocalDate.now();
        long daysToExam = ChronoUnit.DAYS.between(today, examDate);
        int size = tasks.size();
        if (size == 0) return;

        for (int i = 0; i < size; i++) {
            ExtractedTask task = tasks.get(i);
            Priority priority;
            if (i == 0 || i == 1 || daysToExam <= 7) {
                priority = Priority.HIGH;
            } else if (i < size / 2 || daysToExam <= 14) {
                priority = Priority.MEDIUM;
            } else {
                priority = Priority.LOW;
            }
            tasks.set(i, new ExtractedTask(
                    task.title(), task.estimatedMinutes(), task.sequenceOrder(), task.notes(), priority));
        }
    }

    String buildPlanUserPrompt(AgentState state) {
        StringBuilder sb = new StringBuilder();
        sb.append("Current planning state:\n");
        sb.append("- Material ID: ").append(state.materialId).append("\n");
        sb.append("- Course exam date: ").append(state.examDate).append("\n");
        sb.append("- Daily study minutes: ").append(state.dailyStudyMinutes).append("\n");
        sb.append("- Preferred study days: ").append(state.preferredDays).append("\n");
        sb.append("- Incremental mode: ").append(state.isIncremental).append("\n\n");

        sb.append("Progress:\n");
        sb.append("- Tasks extracted: ").append(state.isTasksExtracted() ? "yes (" + state.tasks.size() + " tasks)" : "no").append("\n");
        sb.append("- Priorities assigned: ").append(state.tasks.stream().anyMatch(t -> t.priority() != null) ? "yes" : "no").append("\n");
        sb.append("- Slots queried: ").append(state.areSlotsQueried() ? "yes (" + state.slots.size() + " slots)" : "no").append("\n");
        if (state.areSlotsQueried()) {
            int totalMinutes = state.slots.stream().mapToInt(AvailableSlot::availableMinutes).sum();
            sb.append("  - Total available: ").append(totalMinutes).append(" minutes\n");
        }
        sb.append("- Schedule computed: ").append(state.isScheduled() ? "yes" : "no").append("\n");
        if (state.isScheduled()) {
            sb.append("  - Scheduled: ").append(state.scheduleResult.scheduledParts().size()).append(" parts\n");
            sb.append("  - Unscheduled: ").append(state.scheduleResult.unscheduledTasks().size()).append(" tasks\n");
            sb.append("  - Over capacity: ").append(state.scheduleResult.overCapacity()).append("\n");
        }
        sb.append("- Schedule persisted: ").append(state.isScheduled() && !state.scheduleResult.overCapacity() && state.executionLog.stream().anyMatch(l -> l.contains("Persisted")) ? "yes" : "no").append("\n");
        sb.append("- Iteration: ").append(state.iteration).append("/").append(MAX_PLAN_ITERATIONS).append("\n\n");

        if (!state.executionLog.isEmpty()) {
            sb.append("Execution log:\n");
            for (int i = 0; i < state.executionLog.size(); i++) {
                sb.append(i + 1).append(". ").append(state.executionLog.get(i)).append("\n");
            }
            sb.append("\n");
        }

        sb.append("What is your next action? Respond with ONLY the AgentDecision JSON.");
        return sb.toString();
    }

    String buildRescheduleUserPrompt(AgentState state) {
        StringBuilder sb = new StringBuilder();
        sb.append("Current reschedule state:\n");
        sb.append("- Course ID: ").append(state.courseId).append("\n");
        sb.append("- Daily study minutes: ").append(state.dailyStudyMinutes).append("\n");
        sb.append("- Preferred study days: ").append(state.preferredDays).append("\n");
        sb.append("- Exam date: ").append(state.examDate).append("\n\n");

        sb.append("Progress:\n");
        boolean missedDetected = state.executionLog.stream().anyMatch(l -> l.contains("Detected") && l.contains("missed tasks"));
        sb.append("- Missed tasks detected: ").append(missedDetected ? "yes" : "no").append("\n");
        sb.append("- Full reschedule performed: ").append(state.executionLog.stream().anyMatch(l -> l.contains("full reschedule")) ? "yes" : "no").append("\n");
        sb.append("- Remaining tasks loaded: ").append(state.isTasksExtracted() ? "yes (" + state.tasks.size() + " tasks)" : "no").append("\n");
        sb.append("- Priorities assigned: ").append(state.tasks.stream().anyMatch(t -> t.priority() != null) ? "yes" : "no").append("\n");
        sb.append("- Slots queried: ").append(state.areSlotsQueried() ? "yes (" + state.slots.size() + " slots)" : "no").append("\n");
        sb.append("- Schedule computed: ").append(state.isScheduled() ? "yes" : "no").append("\n");
        sb.append("- Schedule persisted: ").append(state.isScheduled() && state.executionLog.stream().anyMatch(l -> l.contains("Persisted")) ? "yes" : "no").append("\n");
        sb.append("- Iteration: ").append(state.iteration).append("/").append(MAX_RESCHEDULE_ITERATIONS).append("\n\n");

        if (!state.executionLog.isEmpty()) {
            sb.append("Execution log:\n");
            for (int i = 0; i < state.executionLog.size(); i++) {
                sb.append(i + 1).append(". ").append(state.executionLog.get(i)).append("\n");
            }
            sb.append("\n");
        }

        sb.append("What is your next action? Respond with ONLY the AgentDecision JSON.");
        return sb.toString();
    }
}
