package com.smartstudy.planning.service;

import com.smartstudy.planning.dto.request.CreateCourseRequest;
import com.smartstudy.planning.dto.request.CreateMaterialRequest;
import com.smartstudy.planning.dto.request.UpdateCourseRequest;
import com.smartstudy.planning.dto.response.AlertResponse;
import com.smartstudy.planning.dto.response.CourseResponse;
import com.smartstudy.planning.dto.response.CreateMaterialResponse;
import com.smartstudy.planning.dto.response.MaterialResponse;
import com.smartstudy.planning.dto.response.StatusResponse;
import com.smartstudy.planning.model.Course;
import com.smartstudy.planning.model.Material;
import com.smartstudy.planning.model.Task;

import com.smartstudy.planning.model.Priority;
import com.smartstudy.planning.repository.CourseRepository;
import com.smartstudy.planning.repository.MaterialRepository;
import com.smartstudy.planning.repository.TaskRepository;
import com.smartstudy.shared.logging.LoggerFactory;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CourseService {

    private static final Logger log = LoggerFactory.getLogger(CourseService.class);
    private final CourseRepository courseRepository;
    private final MaterialRepository materialRepository;
    private final TaskRepository taskRepository;
    private static final Path MATERIAL_UPLOAD_DIR = Path.of("uploads", "materials");

    @Transactional(readOnly = true)
    public List<CourseResponse> getCourses(String userId) {
        log.info("Fetching courses for userId: {}", userId);
        return courseRepository.findByUserIdAndHiddenFalseOrderByCreatedAtAsc(userId)
                .stream()
                .map(course -> toResponse(userId, course, false, null))
                .toList();
    }

    @Transactional(readOnly = true)
    public CourseResponse getCourse(String userId, UUID courseId) {
        log.info("Fetching course {} for userId: {}", courseId, userId);
        return toResponse(userId, getOwnedCourse(userId, courseId), true, null);
    }

    @Transactional
    public CourseResponse createCourse(String userId, CreateCourseRequest request) {
        log.info("Creating course for userId: {} | name: {}", userId, request.name());
        Course course = Course.builder()
                .userId(userId)
                .name(request.name())
                .courseCode(request.courseCode())
                .imageUrl(request.imageUrl())
                .startDate(request.startDate())
                .examDate(request.examDate())
                .hasMaterials(Boolean.TRUE.equals(request.hasMaterials()))
                .hidden(false)
                .build();
        Course saved = courseRepository.save(course);
        createInitialTask(userId, saved);
        return toResponse(userId, saved, false, "Your roadmap has been generated for " + saved.getName() + ".");
    }

    @Transactional
    public CourseResponse updateCourse(String userId, UUID courseId, UpdateCourseRequest request) {
        log.info("Updating course {} for userId: {}", courseId, userId);
        Course course = getOwnedCourse(userId, courseId);
        if (request.name() != null) {
            course.setName(request.name());
        }
        if (request.courseCode() != null) {
            course.setCourseCode(request.courseCode());
        }
        if (request.imageUrl() != null) {
            course.setImageUrl(request.imageUrl());
        }
        if (request.startDate() != null) {
            course.setStartDate(request.startDate());
        }
        if (request.examDate() != null) {
            course.setExamDate(request.examDate());
        }
        if (request.hasMaterials() != null) {
            course.setHasMaterials(request.hasMaterials());
        }
        if (request.hidden() != null) {
            course.setHidden(request.hidden());
        }
        return toResponse(userId, course, true, "Your roadmap was updated to reflect the course changes.");
    }

    @Transactional
    public StatusResponse deleteCourse(String userId, UUID courseId) {
        log.info("Deleting course {} for userId: {}", courseId, userId);
        Course course = getOwnedCourse(userId, courseId);
        taskRepository.deleteByCourseIdAndUserId(courseId, userId);
        courseRepository.delete(course);
        return new StatusResponse("success", new AlertResponse(course.getName() + " and its tasks have been removed."));
    }

    @Transactional(readOnly = true)
    public List<MaterialResponse> getMaterials(String userId, UUID courseId) {
        log.info("Fetching materials for course {} | userId: {}", courseId, userId);
        getOwnedCourse(userId, courseId);
        return materialRepository.findByCourseIdAndUserIdOrderByUploadedAtAsc(courseId, userId)
                .stream()
                .map(this::toMaterialResponse)
                .toList();
    }

    @Transactional
    public CreateMaterialResponse createMaterial(String userId, UUID courseId, CreateMaterialRequest request) {
        log.info("Creating material for course {} | userId: {} | fileName: {}", courseId, userId, request.fileName());
        Course course = getOwnedCourse(userId, courseId);
        Material material = Material.builder()
                .courseId(courseId)
                .userId(userId)
                .name(request.fileName())
                .contentType(request.contentType())
                .fileSizeBytes(request.fileSizeBytes())
                .pageCount(request.pageCount())
                .status("pending")
                .build();
        Material saved = materialRepository.save(material);
        course.setHasMaterials(true);
        String uploadUrl = "/api/v1/upload/" + saved.getId();
        return new CreateMaterialResponse(saved.getId(), uploadUrl,
                new AlertResponse("New material added - your roadmap will refresh once processing completes."));
    }

    @Transactional
    public MaterialResponse uploadMaterial(String userId, UUID materialId, MultipartFile file) {
        Material material = materialRepository.findByIdAndUserId(materialId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "MATERIAL_NOT_FOUND"));

        if (file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "MATERIAL_FILE_EMPTY");
        }

        if (file.getSize() > material.getFileSizeBytes()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "MATERIAL_FILE_TOO_LARGE");
        }

        try {
            Files.createDirectories(MATERIAL_UPLOAD_DIR);
            Path uploadPath = MATERIAL_UPLOAD_DIR.resolve(materialId.toString());
            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, uploadPath, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "MATERIAL_UPLOAD_FAILED", ex);
        }

        material.setStatus("uploaded");
        return toMaterialResponse(material);
    }

    @Transactional
    public StatusResponse deleteMaterial(String userId, UUID courseId, UUID materialId) {
        log.info("Deleting material {} from course {} | userId: {}", materialId, courseId, userId);
        getOwnedCourse(userId, courseId);
        materialRepository.deleteByIdAndCourseIdAndUserId(materialId, courseId, userId);
        return new StatusResponse("success",
                new AlertResponse("Tasks linked to this material were removed from your roadmap."));
    }

    public Course getOwnedCourse(String userId, UUID courseId) {
        return courseRepository.findByIdAndUserId(courseId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "COURSE_NOT_FOUND"));
    }

    private CourseResponse toResponse(String userId, Course course, boolean includeHidden, String alertMessage) {
        return new CourseResponse(
                course.getId(),
                course.getName(),
                course.getCourseCode(),
                course.getImageUrl(),
                course.getStartDate(),
                course.getExamDate(),
                materialRepository.countByCourseIdAndUserId(course.getId(), userId) > 0 || course.isHasMaterials(),
                includeHidden ? course.isHidden() : null,
                completionPercentage(userId, course.getId()),
                alertMessage != null ? new AlertResponse(alertMessage) : null);
    }

    private MaterialResponse toMaterialResponse(Material material) {
        double sizeMb = material.getFileSizeBytes() / 1_000_000.0;
        return new MaterialResponse(material.getId(), material.getName(), material.getPageCount(), sizeMb,
                material.getStatus(), material.getUploadedAt());
    }

    private double completionPercentage(String userId, UUID courseId) {
        long total = taskRepository.countByUserIdAndCourseId(userId, courseId);
        if (total == 0) {
            return 0.0;
        }
        long completed = taskRepository.countByUserIdAndCourseIdAndCompletedTrue(userId, courseId);
        return Math.round((completed * 10000.0) / total) / 100.0;
    }

    private void createInitialTask(String userId, Course course) {
        taskRepository.save(Task.builder()
                .userId(userId)
                .courseId(course.getId())
                .title(course.getName() + " overview")
                .scheduledDate(course.getStartDate().atZone(ZoneOffset.UTC).toLocalDate())
                .durationMinutes(60)
                .priority(Priority.MEDIUM)

                .completed(false)
                .build());
    }
}
