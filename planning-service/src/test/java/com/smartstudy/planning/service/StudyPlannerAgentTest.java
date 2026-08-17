package com.smartstudy.planning.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartstudy.planning.ai.model.AvailableSlot;
import com.smartstudy.planning.ai.model.ScheduleResult;
import com.smartstudy.planning.ai.model.ScheduledPart;
import com.smartstudy.planning.ai.tool.CalendarQuerierTool;
import com.smartstudy.planning.ai.tool.MissedTaskDetectorTool;
import com.smartstudy.planning.ai.tool.PdfExtractorTool;
import com.smartstudy.planning.ai.tool.SchedulerEngineTool;
import com.smartstudy.planning.model.Priority;
import com.smartstudy.planning.model.Task;
import com.smartstudy.planning.repository.CourseRepository;
import com.smartstudy.planning.repository.TaskRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StudyPlannerAgentTest {

    @Mock private PdfExtractorTool pdfExtractorTool;
    @Mock private CalendarQuerierTool calendarQuerierTool;
    @Spy private SchedulerEngineTool schedulerEngineTool = new SchedulerEngineTool();
    @Mock private MissedTaskDetectorTool missedTaskDetectorTool;
    @Mock private AiSchedulePersistenceService aiSchedulePersistenceService;
    @Mock private TaskRepository taskRepository;
    @Mock private CourseRepository courseRepository;
    @Mock private TaskPriorityService taskPriorityService;
    @Mock private ChatClient.Builder chatClientBuilder;
    @Mock private ObjectMapper objectMapper;

    @InjectMocks
    private StudyPlannerAgent studyPlannerAgent;

    private static final String USER_ID = "user-1";
    private static final UUID COURSE_ID = UUID.randomUUID();

    @SuppressWarnings("unchecked")
    private List<ScheduledPart> captureReplacementParts() {
        ArgumentCaptor<List<ScheduledPart>> captor = ArgumentCaptor.forClass(List.class);
        verify(aiSchedulePersistenceService).replaceTasks(eq(USER_ID), eq(COURSE_ID), anyList(), captor.capture());
        return captor.getValue();
    }

    @Test
    void rescheduleCourseTasks_schedulesOnlyWhatFitsInAvailableSlots() {
        LocalDate studyDay = LocalDate.now().plusDays(1);
        Task first = task("Chapter 1", 60, 1);
        Task second = task("Chapter 2", 60, 2);

        when(calendarQuerierTool.query(eq(USER_ID), eq(COURSE_ID.toString()), eq(60), eq("MON"), eq(null), anyList()))
                .thenReturn(List.of(new AvailableSlot(studyDay, 60)));
        when(taskPriorityService.determinePriority(USER_ID, COURSE_ID, studyDay))
                .thenReturn(Priority.HIGH);

        ScheduleResult result = studyPlannerAgent.rescheduleCourseTasks(USER_ID, COURSE_ID,
                List.of(first, second), 60, "MON");

        assertTrue(result.overCapacity());
        assertEquals(1, result.unscheduledTasks().size());
        List<ScheduledPart> persistedParts = captureReplacementParts();
        assertEquals(1, persistedParts.size());
        assertEquals(studyDay, persistedParts.getFirst().date());
        assertEquals(60, persistedParts.getFirst().minutes());
    }

    @Test
    void rescheduleCourseTasks_recalculatesPriorityFromFinalScheduledDate() {
        LocalDate highPriorityDate = LocalDate.now().plusDays(1);
        LocalDate lowPriorityDate = LocalDate.now().plusDays(8);

        when(calendarQuerierTool.query(eq(USER_ID), eq(COURSE_ID.toString()), eq(40), eq("TUE,THU"), eq(null), anyList()))
                .thenReturn(List.of(
                        new AvailableSlot(highPriorityDate, 40),
                        new AvailableSlot(lowPriorityDate, 40)));
        when(taskPriorityService.determinePriority(USER_ID, COURSE_ID, highPriorityDate))
                .thenReturn(Priority.HIGH);
        when(taskPriorityService.determinePriority(USER_ID, COURSE_ID, lowPriorityDate))
                .thenReturn(Priority.LOW);

        studyPlannerAgent.rescheduleCourseTasks(USER_ID, COURSE_ID,
                List.of(task("Chapter 1", 40, 1), task("Chapter 2", 40, 2)), 40, "TUE,THU");

        List<ScheduledPart> persistedParts = captureReplacementParts();
        assertEquals(Priority.HIGH, persistedParts.get(0).priority());
        assertEquals(Priority.LOW, persistedParts.get(1).priority());
    }

    private Task task(String title, int minutes, int sequence) {
        return Task.builder()
                .id(UUID.randomUUID())
                .userId(USER_ID)
                .courseId(COURSE_ID)
                .title(title)
                .durationMinutes(minutes)
                .priority(Priority.LOW)
                .completed(false)
                .scheduledDate(LocalDate.now().plusDays(sequence))
                .sequenceOrder(sequence)
                .locked(false)
                .missed(false)
                .build();
    }
}
