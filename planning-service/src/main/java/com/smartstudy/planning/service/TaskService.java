package com.smartstudy.planning.service;

import com.smartstudy.planning.dto.request.CreateTaskRequest;
import com.smartstudy.planning.dto.response.TaskResponse;
import com.smartstudy.planning.dto.UpdateTaskRequest;
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

    @Transactional(readOnly = true)
    public List<TaskResponse> getTasks(String userId, LocalDate date) {
        log.info("Fetching tasks for userId: {} | date: {}", userId, date);
        return taskRepository.findByUserIdAndScheduledDateOrderByCreatedAtAsc(userId, date)
                .stream()
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

    @Transactional
    public TaskResponse createTask(String userId, CreateTaskRequest request) {
        log.info("Creating task for userId: {} | title: {}", userId, request.title());
        Task task = Task.builder()
                .userId(userId)
                .courseId(request.courseId())
                .title(request.title())
                .durationMinutes(request.durationMinutes())
                .priority(request.priority())
                .completed(false)
                .scheduledDate(request.date() != null ? request.date() : LocalDate.now())
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
        return taskMapper.toResponse(task);
    }

    @Transactional
    public void deleteTask(String userId, UUID taskId) {
        log.info("Deleting task {} for userId: {}", taskId, userId);
        taskRepository.delete(getOwnedTask(userId, taskId));
    }

    private Task getOwnedTask(String userId, UUID taskId) {
        return taskRepository.findByIdAndUserId(taskId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "TASK_NOT_FOUND"));
    }
}
