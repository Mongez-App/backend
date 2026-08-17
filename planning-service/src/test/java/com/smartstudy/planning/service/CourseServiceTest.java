package com.smartstudy.planning.service;

import com.smartstudy.planning.model.Course;
import com.smartstudy.planning.model.CourseType;
import com.smartstudy.planning.model.Priority;
import com.smartstudy.planning.model.Task;
import com.smartstudy.planning.repository.ChatMessageRepository;
import com.smartstudy.planning.repository.CourseRepository;
import com.smartstudy.planning.repository.EventRepository;
import com.smartstudy.planning.repository.MaterialRepository;
import com.smartstudy.planning.repository.StudyBlockRepository;
import com.smartstudy.planning.repository.TaskRepository;
import com.smartstudy.planning.repository.TeamMemberRepository;
import com.smartstudy.planning.processing.QdrantIndexingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.web.client.RestTemplateBuilder;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CourseServiceTest {

    @Mock private CourseRepository courseRepository;
    @Mock private MaterialRepository materialRepository;
    @Mock private StudyBlockRepository studyBlockRepository;
    @Mock private TaskRepository taskRepository;
    @Mock private TeamMemberRepository teamMemberRepository;
    @Mock private EventRepository eventRepository;
    @Mock private ChatMessageRepository chatMessageRepository;
    @Mock private QdrantIndexingService qdrantIndexingService;
    @Mock private StudyPlannerAgent studyPlannerAgent;
    @Mock private TaskPriorityService taskPriorityService;
    @Mock private RestTemplateBuilder restTemplateBuilder;
    @Mock private FileStorageService fileStorageService;

    @InjectMocks
    private CourseService courseService;

    @Test
    void updateCourse_refreshesMaterialTaskPrioritiesWhenExamDateChanges() {
        String userId = "user-1";
        UUID courseId = UUID.randomUUID();
        Instant newExamDate = LocalDate.now().plusDays(2).atStartOfDay().toInstant(ZoneOffset.UTC);

        Course course = Course.builder()
                .id(courseId)
                .userId(userId)
                .name("Biology")
                .startDate(Instant.now())
                .examDate(null)
                .courseType(CourseType.MATERIAL_COURSE)
                .hidden(false)
                .build();

        Task first = Task.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .courseId(courseId)
                .title("Chapter 1")
                .durationMinutes(60)
                .priority(Priority.LOW)
                .completed(false)
                .scheduledDate(LocalDate.now().plusDays(1))
                .materialId(UUID.randomUUID())
                .build();
        Task second = Task.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .courseId(courseId)
                .title("Chapter 2")
                .durationMinutes(60)
                .priority(Priority.LOW)
                .completed(false)
                .scheduledDate(LocalDate.now().plusDays(8))
                .materialId(UUID.randomUUID())
                .build();
        Task manual = Task.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .courseId(courseId)
                .title("Manual task")
                .durationMinutes(30)
                .priority(Priority.MEDIUM)
                .completed(false)
                .scheduledDate(LocalDate.now().plusDays(3))
                .build();

        when(courseRepository.findByIdAndUserId(courseId, userId)).thenReturn(Optional.of(course));
        when(taskRepository.findByUserIdAndCourseIdOrderByCreatedAtAsc(userId, courseId))
                .thenReturn(List.of(first, second, manual));
        when(taskPriorityService.determinePriority(userId, courseId, first.getScheduledDate()))
                .thenReturn(Priority.HIGH);
        when(taskPriorityService.determinePriority(userId, courseId, second.getScheduledDate()))
                .thenReturn(Priority.LOW);
        when(taskRepository.countByUserIdAndCourseId(userId, courseId)).thenReturn(3L);
        when(taskRepository.countByUserIdAndCourseIdAndCompletedTrue(userId, courseId)).thenReturn(0L);

        courseService.updateCourse(userId, courseId, new com.smartstudy.planning.dto.request.UpdateCourseRequest(
                null, null, null, null, newExamDate, null, null, null));

        ArgumentCaptor<List<Task>> tasksCaptor = ArgumentCaptor.forClass(List.class);
        verify(taskRepository).saveAll(tasksCaptor.capture());
        List<Task> saved = tasksCaptor.getValue();
        assertEquals(2, saved.size());
        assertEquals(Priority.HIGH, saved.get(0).getPriority());
        assertEquals(Priority.LOW, saved.get(1).getPriority());
        assertEquals(newExamDate, course.getExamDate());
    }
}
