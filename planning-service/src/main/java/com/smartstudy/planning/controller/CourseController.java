package com.smartstudy.planning.controller;

import com.smartstudy.planning.dto.request.CreateCourseRequest;
import com.smartstudy.planning.dto.request.CreateMaterialRequest;
import com.smartstudy.planning.dto.request.UpdateCourseRequest;
import com.smartstudy.planning.dto.response.CourseResponse;
import com.smartstudy.planning.dto.response.CreateMaterialResponse;
import com.smartstudy.planning.dto.response.MaterialResponse;
import com.smartstudy.planning.dto.response.StatusResponse;
import com.smartstudy.planning.service.CourseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/courses")
@RequiredArgsConstructor
public class CourseController {

    private final CourseService courseService;

    @GetMapping
    public List<CourseResponse> getCourses(@RequestHeader("X-User-Id") String userId) {
        return courseService.getCourses(userId);
    }

    @GetMapping("/{courseId}")
    public CourseResponse getCourse(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable UUID courseId) {
        return courseService.getCourse(userId, courseId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CourseResponse createCourse(
            @RequestHeader("X-User-Id") String userId,
            @Valid @RequestBody CreateCourseRequest request) {
        return courseService.createCourse(userId, request);
    }

    @PatchMapping("/{courseId}")
    public CourseResponse updateCourse(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable UUID courseId,
            @Valid @RequestBody UpdateCourseRequest request) {
        return courseService.updateCourse(userId, courseId, request);
    }

    @DeleteMapping("/{courseId}")
    public StatusResponse deleteCourse(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable UUID courseId) {
        return courseService.deleteCourse(userId, courseId);
    }

    @GetMapping("/{courseId}/materials")
    public List<MaterialResponse> getMaterials(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable UUID courseId) {
        return courseService.getMaterials(userId, courseId);
    }

    @PostMapping("/{courseId}/materials")
    public CreateMaterialResponse createMaterial(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable UUID courseId,
            @Valid @RequestBody CreateMaterialRequest request) {
        return courseService.createMaterial(userId, courseId, request);
    }

    @DeleteMapping("/{courseId}/materials/{materialId}")
    public StatusResponse deleteMaterial(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable UUID courseId,
            @PathVariable UUID materialId) {
        return courseService.deleteMaterial(userId, courseId, materialId);
    }
}
