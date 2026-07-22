package com.smartstudy.planning.controller;

import com.smartstudy.planning.dto.response.MaterialResponse;
import com.smartstudy.planning.service.CourseService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/upload")
@RequiredArgsConstructor
public class MaterialUploadController {

    private final CourseService courseService;

    @PostMapping("/{materialId}")
    @ResponseStatus(HttpStatus.CREATED)
    public MaterialResponse uploadMaterial(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable UUID materialId,
            @RequestParam("file") MultipartFile file) {
        return courseService.uploadMaterial(userId, materialId, file);
    }
}
