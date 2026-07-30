package com.smartstudy.planning.controller;

import com.smartstudy.planning.dto.request.CreateTaskRequest;
import com.smartstudy.planning.dto.response.TaskResponse;
import com.smartstudy.planning.dto.UpdateTaskRequest;
import com.smartstudy.planning.service.TaskService;
import com.smartstudy.shared.logging.LoggerFactory;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/tasks")
@RequiredArgsConstructor
public class TaskController {

    private static final Logger log = LoggerFactory.getLogger(TaskController.class);
    private final TaskService taskService;

    // X-User-Id is temporary until the Firebase token filter supplies the authenticated uid
    @GetMapping
    public List<TaskResponse> getTasks(
            @RequestHeader("X-User-Id") String userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        log.info("Incoming request: GET /tasks | userId: {} | date: {}", userId, date);
        validateUserId(userId);
        return taskService.getTasks(userId, date);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TaskResponse createTask(
            @RequestHeader("X-User-Id") String userId,
            @Valid @RequestBody CreateTaskRequest request) {
        log.info("Incoming request: POST /tasks | userId: {}", userId);
        validateUserId(userId);
        return taskService.createTask(userId, request);
    }

    @PatchMapping("/{taskId}")
    public TaskResponse updateTask(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable UUID taskId,
            @Valid @RequestBody UpdateTaskRequest request) {
        log.info("Incoming request: PATCH /tasks/{} | userId: {}", taskId, userId);
        validateUserId(userId);
        return taskService.updateTask(userId, taskId, request);
    }

    @DeleteMapping("/{taskId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTask(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable UUID taskId) {
        log.info("Incoming request: DELETE /tasks/{} | userId: {}", taskId, userId);
        validateUserId(userId);
        taskService.deleteTask(userId, taskId);
    }

    private void validateUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new com.smartstudy.shared.exception.BadRequestException("MISSING_USER_ID", "X-User-Id header is required.");
        }
    }
}
