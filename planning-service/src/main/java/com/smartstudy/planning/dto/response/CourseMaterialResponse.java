package com.smartstudy.planning.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

public record CourseMaterialResponse(
        @JsonProperty("material_id") UUID materialId,
        @JsonProperty("name") String name,
        @JsonProperty("file_size_mb") double fileSizeMb,
        @JsonProperty("path") String path,
        @JsonProperty("status") String status,
        @JsonProperty("uploaded_at") Instant uploadedAt
) {}