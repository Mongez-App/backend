package com.smartstudy.planning.service;

import com.smartstudy.planning.ai.model.ScheduledPart;
import com.smartstudy.planning.model.Course;
import com.smartstudy.planning.model.Material;
import com.smartstudy.planning.model.Task;
import com.smartstudy.planning.repository.CourseRepository;
import com.smartstudy.planning.repository.EventRepository;
import com.smartstudy.planning.repository.MaterialRepository;
import com.smartstudy.planning.repository.TaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class AiSchedulePersistenceServiceGuardrailTest {

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private EventRepository eventRepository;

    @Mock
    private MaterialRepository materialRepository;

    @Mock
    private CourseRepository courseRepository;

    @InjectMocks
    private AiSchedulePersistenceService service;

    private static final String USER_ID = "user-1";
    private static final UUID COURSE_ID = UUID.randomUUID();
    private static final UUID MATERIAL_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        Course course = Course.builder().id(COURSE_ID).userId(USER_ID).name("Test Course").build();
        when(courseRepository.findByIdAndUserId(COURSE_ID, USER_ID)).thenReturn(Optional.of(course));
        when(courseRepository.findByIdAndUserId(any(UUID.class), eq("other-user")))
                .thenReturn(Optional.empty());

        Material material = Material.builder().id(MATERIAL_ID).userId(USER_ID).courseId(COURSE_ID).build();
        when(materialRepository.findByIdAndUserId(MATERIAL_ID, USER_ID)).thenReturn(Optional.of(material));
        when(materialRepository.findByIdAndUserId(MATERIAL_ID, "other-user"))
                .thenReturn(Optional.empty());
    }

    @Test
    void persist_shouldThrowWhenCourseNotOwned() {
        var parts = List.of(new ScheduledPart("Task 1", LocalDate.now(), 30, 1, null, null));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                service.persist("other-user", COURSE_ID, MATERIAL_ID, parts, false));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        assertTrue(ex.getReason().contains("COURSE_NOT_OWNED"));
    }

    @Test
    void persist_shouldThrowWhenMaterialNotOwned() {
        UUID otherCourseId = UUID.randomUUID();
        Course otherCourse = Course.builder().id(otherCourseId).userId("other-user").name("Other Course").build();
        when(courseRepository.findByIdAndUserId(otherCourseId, "other-user")).thenReturn(Optional.of(otherCourse));

        var parts = List.of(new ScheduledPart("Task 1", LocalDate.now(), 30, 1, null, null));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                service.persist("other-user", otherCourseId, MATERIAL_ID, parts, false));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        assertTrue(ex.getReason().contains("MATERIAL_NOT_OWNED"));
    }

    @Test
    void persist_shouldSkipTasksOnCompletedDates() {
        LocalDate completedDate = LocalDate.now().plusDays(1);
        Task completedTask = Task.builder()
                .userId(USER_ID).courseId(COURSE_ID).title("Existing").durationMinutes(30)
                .completed(true).scheduledDate(completedDate).build();

        when(taskRepository.findByUserIdAndCourseIdAndScheduledDateBetweenAndCompletedTrue(
                eq(USER_ID), eq(COURSE_ID), eq(completedDate), eq(completedDate)))
                .thenReturn(List.of(completedTask));
        when(taskRepository.findByUserIdAndCourseIdAndScheduledDateBetweenAndMissedTrue(
                eq(USER_ID), eq(COURSE_ID), eq(completedDate), eq(completedDate)))
                .thenReturn(List.of());
        when(taskRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        var parts = List.of(
                new ScheduledPart("New Task", completedDate, 30, 1, null, null),
                new ScheduledPart("Another Task", LocalDate.now().plusDays(5), 45, 2, null, null)
        );

        var result = service.persist(USER_ID, COURSE_ID, MATERIAL_ID, parts, false);

        assertEquals(1, result.skippedCount());
        assertEquals(1, result.createdCount());
    }

    @Test
    void persist_shouldSkipTasksOnMissedDates() {
        LocalDate missedDate = LocalDate.now().plusDays(2);
        Task missedTask = Task.builder()
                .userId(USER_ID).courseId(COURSE_ID).title("Missed").durationMinutes(30)
                .missed(true).scheduledDate(missedDate).build();

        when(taskRepository.findByUserIdAndCourseIdAndScheduledDateBetweenAndCompletedTrue(
                eq(USER_ID), eq(COURSE_ID), eq(missedDate), eq(missedDate)))
                .thenReturn(List.of());
        when(taskRepository.findByUserIdAndCourseIdAndScheduledDateBetweenAndMissedTrue(
                eq(USER_ID), eq(COURSE_ID), eq(missedDate), eq(missedDate)))
                .thenReturn(List.of(missedTask));
        when(taskRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        var parts = List.of(
                new ScheduledPart("New Task", missedDate, 30, 1, null, null),
                new ScheduledPart("Another Task", LocalDate.now().plusDays(5), 45, 2, null, null)
        );

        var result = service.persist(USER_ID, COURSE_ID, MATERIAL_ID, parts, false);

        assertEquals(1, result.skippedCount());
        assertEquals(1, result.createdCount());
    }

    @Test
    void persist_shouldDeduplicateIncrementalTasks() {
        LocalDate date = LocalDate.now().plusDays(1);
        Task existingTask = Task.builder()
                .userId(USER_ID).courseId(COURSE_ID).materialId(MATERIAL_ID)
                .title("Chapter 1").durationMinutes(45).scheduledDate(date).build();

        when(taskRepository.findByUserIdAndCourseIdAndMaterialIdAndScheduledDateBetween(
                eq(USER_ID), eq(COURSE_ID), eq(MATERIAL_ID), any(), any()))
                .thenReturn(List.of(existingTask));
        when(taskRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        var parts = List.of(
                new ScheduledPart("Chapter 1", date, 45, 1, null, null),
                new ScheduledPart("Chapter 2", LocalDate.now().plusDays(3), 60, 2, null, null)
        );

        var result = service.persist(USER_ID, COURSE_ID, MATERIAL_ID, parts, true);

        assertEquals(1, result.skippedCount());
        assertEquals(1, result.createdCount());
    }

    @Test
    void fullReschedule_shouldThrowWhenCourseNotOwned() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                service.fullReschedule("other-user", COURSE_ID));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        assertTrue(ex.getReason().contains("COURSE_NOT_OWNED"));
    }
}
