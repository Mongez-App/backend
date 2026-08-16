package com.smartstudy.planning.service;

import com.smartstudy.planning.ai.model.AvailableSlot;
import com.smartstudy.planning.ai.model.ExtractedTask;
import com.smartstudy.planning.ai.model.ScheduleResult;
import com.smartstudy.planning.ai.model.ScheduledPart;
import com.smartstudy.planning.ai.tool.SchedulerEngineTool;
import com.smartstudy.planning.model.Material;
import com.smartstudy.planning.model.MaterialStatus;
import com.smartstudy.planning.model.Priority;
import com.smartstudy.planning.model.Task;
import com.smartstudy.planning.repository.EventRepository;
import com.smartstudy.planning.repository.MaterialRepository;
import com.smartstudy.planning.repository.TaskRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * A task must keep pointing at the material it came from, or deleting that
 * material leaves its tasks behind — the link is what deleteTasksForMaterial
 * queries on.
 */
@ExtendWith(MockitoExtension.class)
class AiSchedulePersistenceServiceTest {

    @Mock private TaskRepository taskRepository;
    @Mock private EventRepository eventRepository;
    @Mock private MaterialRepository materialRepository;

    @InjectMocks
    private AiSchedulePersistenceService service;

    private final SchedulerEngineTool scheduler = new SchedulerEngineTool();

    private static final String USER_ID = "user-1";
    private static final UUID COURSE_ID = UUID.randomUUID();

    @SuppressWarnings("unchecked")
    private List<Task> capturePersistedTasks() {
        ArgumentCaptor<List<Task>> captor = ArgumentCaptor.forClass(List.class);
        verify(taskRepository).saveAll(captor.capture());
        return captor.getValue();
    }

    private void stubSaves() {
        when(taskRepository.saveAll(anyList())).thenAnswer(invocation -> {
            List<Task> tasks = invocation.getArgument(0);
            tasks.forEach(task -> task.setId(UUID.randomUUID()));
            return tasks;
        });
    }

    @Test
    void persist_keepsThePartsOwnMaterialWhenNoneIsPassed() {
        // The reschedule path re-plans tasks from several materials at once, so
        // it passes no single materialId — the link has to survive on the part.
        UUID materialA = UUID.randomUUID();
        UUID materialB = UUID.randomUUID();
        stubSaves();

        service.persist(USER_ID, COURSE_ID, null, List.of(
                new ScheduledPart("Chapter 1", LocalDate.now(), 60, 0, null, null,
                        null, List.of(), Priority.MEDIUM, materialA),
                new ScheduledPart("Chapter 2", LocalDate.now(), 60, 1, null, null,
                        null, List.of(), Priority.MEDIUM, materialB)), false);

        List<Task> persisted = capturePersistedTasks();
        assertEquals(materialA, persisted.get(0).getMaterialId());
        assertEquals(materialB, persisted.get(1).getMaterialId());
        verifyNoInteractions(materialRepository);
    }

    @Test
    void persist_fallsBackToTheMaterialBeingProcessed() {
        UUID materialId = UUID.randomUUID();
        stubSaves();
        when(materialRepository.findByIdAndUserId(materialId, USER_ID))
                .thenReturn(Optional.of(Material.builder()
                        .id(materialId)
                        .status(MaterialStatus.PROCESSING)
                        .build()));

        service.persist(USER_ID, COURSE_ID, materialId, List.of(
                new ScheduledPart("Chapter 1", LocalDate.now(), 60, 0, null, null)), false);

        assertEquals(materialId, capturePersistedTasks().get(0).getMaterialId());
    }

    @Test
    void scheduler_carriesTheMaterialLinkOntoEverySplitPart() {
        UUID materialId = UUID.randomUUID();
        ExtractedTask task = new ExtractedTask("Long chapter", 180, 0, null, List.of(),
                Priority.HIGH, materialId);

        ScheduleResult result = scheduler.schedule(List.of(task), List.of(
                new AvailableSlot(LocalDate.now(), 60),
                new AvailableSlot(LocalDate.now().plusDays(1), 60),
                new AvailableSlot(LocalDate.now().plusDays(2), 60)));

        assertEquals(3, result.scheduledParts().size());
        assertTrue(result.scheduledParts().stream()
                .allMatch(part -> materialId.equals(part.materialId())));
    }
}
