package com.smartstudy.planning.service;

import com.smartstudy.planning.ai.model.*;
import com.smartstudy.planning.ai.tool.CalendarQuerierTool;
import com.smartstudy.planning.ai.tool.MissedTaskDetectorTool;
import com.smartstudy.planning.ai.tool.PdfExtractorTool;
import com.smartstudy.planning.ai.tool.SchedulerEngineTool;
import com.smartstudy.planning.model.Course;
import com.smartstudy.planning.model.Priority;
import com.smartstudy.planning.repository.CourseRepository;
import com.smartstudy.planning.repository.TaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StudyPlannerAgentTest {

    @Mock
    private PdfExtractorTool pdfExtractorTool;

    @Mock
    private CalendarQuerierTool calendarQuerierTool;

    @Mock
    private SchedulerEngineTool schedulerEngineTool;

    @Mock
    private MissedTaskDetectorTool missedTaskDetectorTool;

    @Mock
    private AiSchedulePersistenceService aiSchedulePersistenceService;

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private CourseRepository courseRepository;

    @Mock
    private ChatClient.Builder chatClientBuilder;

    @Mock(answer = org.mockito.Answers.RETURNS_DEEP_STUBS)
    private ChatClient chatClient;

    private StudyPlannerAgent agent;

    private final String userId = "user-123";
    private final UUID courseId = UUID.randomUUID();
    private final UUID materialId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        agent = new StudyPlannerAgent(
                pdfExtractorTool, calendarQuerierTool, schedulerEngineTool,
                missedTaskDetectorTool, aiSchedulePersistenceService,
                taskRepository, courseRepository, chatClientBuilder,
                new com.fasterxml.jackson.databind.ObjectMapper()
        );
    }

    @Test
    void testCallLLMForDecision_validJson_returnsDecision() {
        when(chatClientBuilder.build()).thenReturn(chatClient);
        String json = "{\"action\":\"finish\",\"tool\":\"finish\",\"arguments\":{\"status\":\"scheduled\",\"message\":\"done\"}}";
        when(chatClient.prompt().system(anyString()).user(anyString()).call().content()).thenReturn(json);

        AgentDecision decision = agent.callLLMForDecision("system", "user");

        assertNotNull(decision);
        assertEquals("finish", decision.action());
        assertEquals("finish", decision.tool());
        assertEquals("scheduled", decision.arguments().get("status"));
    }

    @Test
    void testCallLLMForDecision_invalidJson_returnsNull() {
        when(chatClientBuilder.build()).thenReturn(chatClient);
        when(chatClient.prompt().system(anyString()).user(anyString()).call().content()).thenReturn("not json");

        AgentDecision decision = agent.callLLMForDecision("system", "user");

        assertNull(decision);
    }

    @Test
    void testCallLLMForDecision_jsonWithMarkdownFences_parsesCorrectly() {
        when(chatClientBuilder.build()).thenReturn(chatClient);
        String json = "```json\n{\"action\":\"call_tool\",\"tool\":\"extract_and_parse_tasks\",\"arguments\":{\"materialId\":\"abc\"}}\n```";
        when(chatClient.prompt().system(anyString()).user(anyString()).call().content()).thenReturn(json);

        AgentDecision decision = agent.callLLMForDecision("system", "user");

        assertNotNull(decision);
        assertEquals("call_tool", decision.action());
        assertEquals("extract_and_parse_tasks", decision.tool());
    }

    @Test
    void testExecutePlanDecision_finish_returnsNull() {
        AgentState state = new AgentState(userId, courseId, materialId, 60, "MON,WED,FRI", false);

        String result = agent.executePlanDecision(
                new AgentDecision("finish", "finish", Map.of("status", "scheduled", "message", "done")),
                state);

        assertNull(result);
    }

    @Test
    void testExecutePlanDecision_unknownTool_returnsError() {
        AgentState state = new AgentState(userId, courseId, materialId, 60, "MON,WED,FRI", false);

        String result = agent.executePlanDecision(
                new AgentDecision("call_tool", "unknown_tool", Map.of()),
                state);

        assertEquals("Unknown tool: unknown_tool", result);
    }

    @Test
    void testExecutePlanDecision_invalidAction_returnsError() {
        AgentState state = new AgentState(userId, courseId, materialId, 60, "MON,WED,FRI", false);

        String result = agent.executePlanDecision(
                new AgentDecision("invalid_action", "tool", Map.of()),
                state);

        assertTrue(result.contains("Invalid action"));
    }

    @Test
    void testExecuteExtractAndParseTasks_success() {
        AgentState state = new AgentState(userId, courseId, materialId, 60, "MON,WED,FRI", false);
        LocalDate examDate = LocalDate.now().plusDays(30);
        state.examDate = examDate;

        List<ExtractedTask> tasks = new java.util.ArrayList<>(List.of(
                new ExtractedTask("Chapter 1", 45, 1, null, null),
                new ExtractedTask("Chapter 2", 60, 2, null, null)
        ));
        when(pdfExtractorTool.extractAndParseTasks(materialId.toString())).thenReturn(
                new TaskExtractionResult("Extracted 2 tasks: Chapter 1(45m), Chapter 2(60m)", tasks));

        String result = agent.executeExtractAndParseTasks(state);

        assertEquals(2, state.tasks.size());
        assertEquals(Priority.HIGH, state.tasks.get(0).priority());
        assertEquals(Priority.HIGH, state.tasks.get(1).priority());
        assertTrue(result.contains("Extracted 2 tasks"));
        assertTrue(state.executionLog.get(0).contains("Extracted 2 tasks"));
    }

    @Test
    void testExecuteExtractAndParseTasks_alreadyExtracted_returnsSkipMessage() {
        AgentState state = new AgentState(userId, courseId, materialId, 60, "MON,WED,FRI", false);
        state.tasks = List.of(new ExtractedTask("Ch1", 45, 1, null, null));

        String result = agent.executeExtractAndParseTasks(state);

        assertTrue(result.contains("already extracted"));
        verify(pdfExtractorTool, never()).extractAndParseTasks(any());
    }

    @Test
    void testExecuteQueryAvailableSlots_success() {
        AgentState state = new AgentState(userId, courseId, materialId, 60, "MON,WED,FRI", false);
        state.tasks = List.of(new ExtractedTask("Ch1", 45, 1, null, null));

        List<AvailableSlot> slots = List.of(
                new AvailableSlot(LocalDate.now().plusDays(1), 60),
                new AvailableSlot(LocalDate.now().plusDays(3), 60)
        );
        when(calendarQuerierTool.query(userId, courseId.toString(), 60, "MON,WED,FRI")).thenReturn(slots);

        String result = agent.executeQueryAvailableSlots(state);

        assertEquals(2, state.slots.size());
        assertTrue(result.contains("2 available slots"));
    }

    @Test
    void testExecuteScheduleTasks_success() {
        AgentState state = new AgentState(userId, courseId, materialId, 60, "MON,WED,FRI", false);
        state.tasks = List.of(new ExtractedTask("Ch1", 45, 1, null, Priority.HIGH));
        state.slots = List.of(new AvailableSlot(LocalDate.now().plusDays(1), 60));

        ScheduledPart part = new ScheduledPart("Ch1", LocalDate.now().plusDays(1), 45, 1, null, null, Priority.HIGH);
        ScheduleResult scheduleResult = new ScheduleResult(List.of(part), List.of(), false);
        when(schedulerEngineTool.schedule(any(), any())).thenReturn(scheduleResult);

        String result = agent.executeScheduleTasks(state);

        assertNotNull(state.scheduleResult);
        assertEquals(1, state.scheduleResult.scheduledParts().size());
        assertFalse(state.scheduleResult.overCapacity());
        assertTrue(result.contains("Scheduled 1 task part"));
    }

    @Test
    void testExecutePersistSchedule_success() {
        AgentState state = new AgentState(userId, courseId, materialId, 60, "MON,WED,FRI", false);
        ScheduledPart part = new ScheduledPart("Ch1", LocalDate.now().plusDays(1), 45, 1, null, null, Priority.HIGH);
        state.scheduleResult = new ScheduleResult(List.of(part), List.of(), false);

        String result = agent.executePersistSchedule(state);

        verify(aiSchedulePersistenceService).persist(eq(userId), eq(courseId), eq(materialId), any(), eq(false));
        assertTrue(result.contains("Persisted 1 scheduled part"));
    }

    @Test
    void testExecutePersistSchedule_overCapacity_returnsError() {
        AgentState state = new AgentState(userId, courseId, materialId, 60, "MON,WED,FRI", false);
        ExtractedTask unscheduled = new ExtractedTask("Ch2", 60, 2, null, Priority.LOW);
        state.scheduleResult = new ScheduleResult(List.of(), List.of(unscheduled), true);

        String result = agent.executePersistSchedule(state);

        verify(aiSchedulePersistenceService, never()).persist(any(), any(), any(), any(), anyBoolean());
        assertTrue(result.contains("over capacity"));
    }

    @Test
    void testGeneratePlan_maxIterationsReached_returnsError() {
        Course course = new Course();
        course.setId(courseId);
        course.setUserId(userId);
        course.setExamDate(Instant.now().plusSeconds(30 * 24 * 60 * 60));
        when(courseRepository.findByIdAndUserId(courseId, userId)).thenReturn(Optional.of(course));

        when(chatClientBuilder.build()).thenReturn(chatClient);
        when(chatClient.prompt().system(anyString()).user(anyString()).call().content())
                .thenReturn("{\"action\":\"call_tool\",\"tool\":\"extract_and_parse_tasks\",\"arguments\":{}}");

        AgentPlanResult result = agent.generatePlan(userId, courseId, materialId, 60, "MON,WED,FRI", false);

        assertEquals("error", result.status());
    }

    @Test
    void testGeneratePlan_courseNotFound_throwsException() {
        when(courseRepository.findByIdAndUserId(courseId, userId)).thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class,
                () -> agent.generatePlan(userId, courseId, materialId, 60, "MON,WED,FRI", false));
    }
}
