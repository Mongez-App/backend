package com.smartstudy.planning.service;

import com.smartstudy.planning.dto.request.CreateTaskRequest;
import com.smartstudy.planning.dto.response.TaskResponse;
import com.smartstudy.planning.dto.UpdateTaskRequest;
import com.smartstudy.planning.model.Task;
import com.smartstudy.planning.repository.TaskRepository;
import com.smartstudy.planning.dto.TaskMapper;
import lombok.RequiredArgsConstructor;
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

    private final TaskRepository taskRepository;
    private final TaskMapper taskMapper;

    @Transactional(readOnly = true)
    public List<TaskResponse> getTasks(String userId, LocalDate date) {
        return taskRepository.findByUserIdAndScheduledDateOrderByCreatedAtAsc(userId, date)
                .stream()
                .map(taskMapper::toResponse)
                .toList();
    }

    @Transactional
    public TaskResponse createTask(String userId, CreateTaskRequest request) {
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
        taskRepository.delete(getOwnedTask(userId, taskId));
    }

    private Task getOwnedTask(String userId, UUID taskId) {
        return taskRepository.findByIdAndUserId(taskId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "TASK_NOT_FOUND"));
    }
}
