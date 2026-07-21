package com.smartstudy.planning.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

public record MaterialResponse(
        @JsonProperty("material_id") UUID materialId,
        @JsonProperty("name") String name,
        @JsonProperty("page_count") Integer pageCount,
        @JsonProperty("file_size_mb") double fileSizeMb,
        @JsonProperty("status") String status,
        @JsonProperty("uploaded_at") Instant uploadedAt
) {
}
