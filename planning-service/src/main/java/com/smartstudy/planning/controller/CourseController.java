package com.smartstudy.planning.controller;

import com.smartstudy.planning.ai.model.AgentCheckResult;
import com.smartstudy.planning.dto.request.CreateCourseRequest;
import com.smartstudy.planning.dto.request.UpdateCourseRequest;
import com.smartstudy.planning.dto.response.CourseResponse;
import com.smartstudy.planning.dto.response.MaterialResponse;
import com.smartstudy.planning.dto.response.StatusResponse;
import com.smartstudy.planning.service.CourseService;
import com.smartstudy.planning.service.StudyPlannerAgent;
import com.smartstudy.shared.logging.LoggerFactory;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
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
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/courses")
@RequiredArgsConstructor
public class CourseController {

    private static final Logger log = LoggerFactory.getLogger(CourseController.class);
    private final CourseService courseService;
    private final StudyPlannerAgent studyPlannerAgent;

    @GetMapping
    public List<CourseResponse> getCourses(@RequestHeader("X-User-Id") String userId) {
        log.info("Incoming request: GET /courses | userId: {}", userId);
        return courseService.getCourses(userId);
    }

    @GetMapping("/{courseId}")
    public CourseResponse getCourse(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable UUID courseId) {
        log.info("Incoming request: GET /courses/{} | userId: {}", courseId, userId);
        return courseService.getCourse(userId, courseId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CourseResponse createCourse(
            @RequestHeader("X-User-Id") String userId,
            @Valid @RequestBody CreateCourseRequest request) {
        log.info("Incoming request: POST /courses | userId: {}", userId);
        return courseService.createCourse(userId, request);
    }

    @PatchMapping("/{courseId}")
    public CourseResponse updateCourse(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable UUID courseId,
            @Valid @RequestBody UpdateCourseRequest request) {
        log.info("Incoming request: PATCH /courses/{} | userId: {}", courseId, userId);
        return courseService.updateCourse(userId, courseId, request);
    }

    @DeleteMapping("/{courseId}")
    public StatusResponse deleteCourse(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable UUID courseId) {
        log.info("Incoming request: DELETE /courses/{} | userId: {}", courseId, userId);
        return courseService.deleteCourse(userId, courseId);
    }

    @GetMapping("/{courseId}/materials")
    public List<MaterialResponse> getMaterials(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable UUID courseId) {
        log.info("Incoming request: GET /courses/{}/materials | userId: {}", courseId, userId);
        return courseService.getMaterials(userId, courseId);
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
        return courseService.createMaterial(userId, courseId, file, dailyStudyMinutes, preferredDays);
    }

    @PostMapping("/{courseId}/reschedule")
    public StatusResponse checkSchedule(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable UUID courseId,
            @RequestHeader(value = "X-Daily-Study-Minutes", defaultValue = "60") int dailyStudyMinutes,
            @RequestHeader(value = "X-Preferred-Days", defaultValue = "MON,TUE,WED,THU,FRI,SAT,SUN") String preferredDays) {
        log.info("Check-schedule request: userId={} courseId={} daily={} days={}", userId, courseId, dailyStudyMinutes, preferredDays);
        courseService.getOwnedCourse(userId, courseId);
        AgentCheckResult result = studyPlannerAgent.checkAndReschedule(userId, courseId, dailyStudyMinutes, preferredDays);
        return new StatusResponse(result.status(), result.alert());
    }

    @DeleteMapping("/{courseId}/materials/{materialId}")
    public StatusResponse deleteMaterial(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable UUID courseId,
            @PathVariable UUID materialId) {
        log.info("Incoming request: DELETE /courses/{}/materials/{} | userId: {}", courseId, materialId, userId);
        return courseService.deleteMaterial(userId, courseId, materialId);
    }
}
