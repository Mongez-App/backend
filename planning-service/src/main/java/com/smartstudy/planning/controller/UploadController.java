package com.smartstudy.planning.controller;

import com.smartstudy.planning.dto.response.UploadMaterialResponse;
import com.smartstudy.planning.service.CourseService;
import com.smartstudy.shared.exception.BadRequestException;
import com.smartstudy.shared.logging.LoggerFactory;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/upload")
@RequiredArgsConstructor
public class UploadController {

    private static final Logger log = LoggerFactory.getLogger(UploadController.class);
    private final CourseService courseService;

    /**
     * Step 2 of two-step upload: upload the binary PDF file for a PENDING material.
     *
     * POST /upload/{materialId}
     */
    @PostMapping("/{materialId}")
    @ResponseStatus(HttpStatus.CREATED)
    public UploadMaterialResponse uploadMaterial(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable UUID materialId,
            @RequestParam("file") MultipartFile file,
            @RequestHeader(value = "X-Daily-Study-Minutes", defaultValue = "60") int dailyStudyMinutes,
            @RequestHeader(value = "X-Preferred-Days", defaultValue = "MON,TUE,WED,THU,FRI,SAT,SUN") String preferredDays) {
        log.info("Incoming request: POST /upload/{} | userId: {}", materialId, userId);
        validateUserId(userId);
        return courseService.uploadMaterialFile(userId, materialId, file, dailyStudyMinutes, preferredDays);
    }

    private void validateUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new BadRequestException("MISSING_USER_ID", "X-User-Id header is required.");
        }
    }
}
