package com.smartstudy.planning.service;

import com.smartstudy.planning.dto.request.CreateTaskRequest;
import com.smartstudy.planning.dto.response.TaskResponse;
import com.smartstudy.planning.dto.UpdateTaskRequest;
import com.smartstudy.planning.model.Priority;
import com.smartstudy.planning.model.Task;
import com.smartstudy.planning.repository.CourseRepository;
import com.smartstudy.planning.repository.TaskRepository;
import com.smartstudy.planning.dto.TaskMapper;
import com.smartstudy.shared.logging.LoggerFactory;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TaskService {

    private static final Logger log = LoggerFactory.getLogger(TaskService.class);
    private final TaskRepository taskRepository;
    private final CourseRepository courseRepository;
    private final TaskMapper taskMapper;
    private final TaskPriorityService taskPriorityService;
    private final CourseService courseService;

    @Transactional(readOnly = true)
    public List<TaskResponse> getTasks(String userId, LocalDate date) {
        log.info("Fetching tasks for userId: {} | date: {}", userId, date);
        List<Task> tasks = date != null
                ? taskRepository.findByUserIdAndScheduledDateOrderByCreatedAtAsc(userId, date)
                : taskRepository.findByUserIdOrderByCreatedAtAsc(userId);
        return tasks.stream()
                .map(taskMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TaskResponse> getTasksByCourse(String userId, UUID courseId, LocalDate date) {
        log.info("Fetching tasks for userId: {} | courseId: {} | date: {}", userId, courseId, date);
        courseRepository.findByIdAndUserId(courseId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "COURSE_NOT_FOUND"));

        List<Task> tasks = date != null
                ? taskRepository.findByUserIdAndCourseIdAndScheduledDateOrderByCreatedAtAsc(userId, courseId, date)
                : taskRepository.findByUserIdAndCourseIdOrderByCreatedAtAsc(userId, courseId);

        return tasks.stream()
                .map(taskMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public TaskResponse getTask(String userId, UUID taskId) {
        log.info("Fetching task {} for userId: {}", taskId, userId);
        return taskMapper.toResponse(getOwnedTask(userId, taskId));
    }

    @Transactional
    public TaskResponse createTask(String userId, CreateTaskRequest request) {
        log.info("Creating task for userId: {} | title: {}", userId, request.title());
        // The course must exist and be visible to the caller (owned or a team
        // course the user is a member of) — never link tasks to foreign courses.
        courseService.getOwnedCourse(userId, request.courseId());
        LocalDate scheduledDate = request.date() != null ? request.date() : LocalDate.now();
        Priority priority = request.priority() != null
                ? request.priority()
                : taskPriorityService.determinePriority(userId, request.courseId(), scheduledDate);
        Task task = Task.builder()
                .userId(userId)
                .courseId(request.courseId())
                .title(request.title())
                .durationMinutes(request.durationMinutes())
                .activeSpentTime(request.activeSpentTime() != null ? request.activeSpentTime() : 0)
                .priority(priority)
                .completed(false)
                .scheduledDate(scheduledDate)
                .sequenceOrder(request.sequenceOrder() != null ? request.sequenceOrder() : 0)
                .build();
        return taskMapper.toResponse(taskRepository.save(task));
    }

    @Transactional
    public TaskResponse updateTask(String userId, UUID taskId, UpdateTaskRequest request) {
        log.info("Updating task {} for userId: {}", taskId, userId);
        Task task = getOwnedTask(userId, taskId);
        if (request.title() != null) {
            task.setTitle(request.title());
        }
        if (request.durationMinutes() != null) {
            task.setDurationMinutes(request.durationMinutes());
        }
        if (request.priority() != null) {
            task.setPriority(request.priority());
        }
        if (request.isCompleted() != null) {
            task.setCompleted(request.isCompleted());
        }
        if (request.date() != null) {
            task.setScheduledDate(request.date());
        }
        if (request.sequenceOrder() != null) {
            task.setSequenceOrder(request.sequenceOrder());
        }
        if (request.activeSpentTime() != null) {
            task.setActiveSpentTime(request.activeSpentTime());
        }
        return taskMapper.toResponse(task);
    }

    @Transactional
    public void deleteTask(String userId, UUID taskId) {
        log.info("Deleting task {} for userId: {}", taskId, userId);
        taskRepository.delete(getOwnedTask(userId, taskId));
    }

    private Task getOwnedTask(String userId, UUID taskId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "TASK_NOT_FOUND"));
        if (!userId.equals(task.getUserId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "TASK_NOT_OWNED");
        }
        return task;
    }
}
