package com.smartstudy.planning.controller;

import com.smartstudy.planning.dto.request.CreateCourseRequest;
import com.smartstudy.planning.dto.request.UpdateCourseRequest;
import com.smartstudy.planning.dto.response.CourseEventResponse;
import com.smartstudy.planning.dto.response.CourseMaterialResponse;
import com.smartstudy.planning.dto.response.CourseResponse;
import com.smartstudy.planning.dto.response.CourseTasksResponse;
import com.smartstudy.planning.dto.response.MaterialResponse;
import com.smartstudy.planning.dto.response.StatusResponse;
import com.smartstudy.planning.dto.request.CreateCourseEventRequest;
import com.smartstudy.planning.dto.response.AlertResponse;
import com.smartstudy.planning.service.CourseService;
import com.smartstudy.planning.service.EventService;
import com.smartstudy.planning.service.TaskService;
import com.smartstudy.shared.exception.BadRequestException;
import com.smartstudy.shared.logging.LoggerFactory;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/courses")
@RequiredArgsConstructor
public class CourseController {

    private static final Logger log = LoggerFactory.getLogger(CourseController.class);
    private final CourseService courseService;
    private final TaskService taskService;
    private final EventService eventService;

    @GetMapping
    public List<CourseResponse> getCourses(@RequestHeader("X-User-Id") String userId) {
        log.info("Incoming request: GET /courses | userId: {}", userId);
        validateUserId(userId);
        return courseService.getCourses(userId);
    }

    @GetMapping("/{courseId}")
    public CourseResponse getCourse(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable UUID courseId) {
        log.info("Incoming request: GET /courses/{} | userId: {}", courseId, userId);
        validateUserId(userId);
        return courseService.getCourse(userId, courseId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CourseResponse createCourse(
            @RequestHeader("X-User-Id") String userId,
            @Valid @RequestBody CreateCourseRequest request,
            @RequestHeader(value = "X-Daily-Study-Minutes", defaultValue = "60") int dailyStudyMinutes) {
        log.info("Incoming request: POST /courses | userId: {}", userId);
        validateUserId(userId);
        return courseService.createCourse(userId, request, dailyStudyMinutes);
    }

    @PatchMapping("/{courseId}")
    public CourseResponse updateCourse(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable UUID courseId,
            @Valid @RequestBody UpdateCourseRequest request) {
        log.info("Incoming request: PATCH /courses/{} | userId: {}", courseId, userId);
        validateUserId(userId);
        return courseService.updateCourse(userId, courseId, request);
    }

    @DeleteMapping("/{courseId}")
    public StatusResponse deleteCourse(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable UUID courseId) {
        log.info("Incoming request: DELETE /courses/{} | userId: {}", courseId, userId);
        validateUserId(userId);
        return courseService.deleteCourse(userId, courseId);
    }

    @GetMapping("/{courseId}/materials")
    public List<CourseMaterialResponse> getCourseMaterials(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable UUID courseId) {
        log.info("Incoming request: GET /courses/{}/materials | userId: {}", courseId, userId);
        validateUserId(userId);
        return courseService.getCourseMaterials(userId, courseId);
    }

    @GetMapping("/{courseId}/tasks")
    public CourseTasksResponse getCourseTasks(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable UUID courseId) {
        log.info("Incoming request: GET /courses/{}/tasks | userId: {}", courseId, userId);
        validateUserId(userId);
        return taskService.getCourseTasksWithMeta(userId, courseId);
    }

    @GetMapping("/{courseId}/events")
    public List<CourseEventResponse> getCourseEvents(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable UUID courseId) {
        log.info("Incoming request: GET /courses/{}/events | userId: {}", courseId, userId);
        validateUserId(userId);
        return courseService.getCourseEvents(userId, courseId);
    }

    @PostMapping("/{courseId}/materials")
    @ResponseStatus(HttpStatus.CREATED)
    public MaterialResponse createMaterial(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable UUID courseId,
            @RequestParam("file") MultipartFile file,
            @RequestHeader(value = "X-Daily-Study-Minutes", defaultValue = "60") int dailyStudyMinutes,
            @RequestHeader(value = "X-Preferred-Days", defaultValue = "MON,TUE,WED,THU,FRI,SAT,SUN") String preferredDays) {
        log.info("Incoming request: POST /courses/{}/materials | userId: {}", courseId, userId);
        validateUserId(userId);
        return courseService.createMaterial(userId, courseId, file, dailyStudyMinutes, preferredDays);
    }

    @DeleteMapping("/{courseId}/materials/{materialId}")
    public StatusResponse deleteMaterial(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable UUID courseId,
            @PathVariable UUID materialId) {
        log.info("Incoming request: DELETE /courses/{}/materials/{} | userId: {}", courseId, materialId, userId);
        validateUserId(userId);
        return courseService.deleteMaterial(userId, courseId, materialId);
    }

    @PostMapping("/{courseId}/events")
    @ResponseStatus(HttpStatus.CREATED)
    public AlertResponse createEvent(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable UUID courseId,
            @Valid @RequestBody CreateCourseEventRequest request) {
        log.info("Incoming request: POST /courses/{}/events | userId: {}", courseId, userId);
        validateUserId(userId);
        return eventService.createCourseEvent(userId, courseId, request);
    }

    private void validateUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new BadRequestException("MISSING_USER_ID", "X-User-Id header is required.");
        }
    }
}
