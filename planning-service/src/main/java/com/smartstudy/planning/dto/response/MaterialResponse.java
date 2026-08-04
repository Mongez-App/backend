package com.smartstudy.planning.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record MaterialResponse(
        @JsonProperty("material_id") UUID materialId,
        @JsonProperty("name") String name,
        @JsonProperty("content_type") String contentType,
        @JsonProperty("file_size_bytes") Long fileSizeBytes,
        @JsonProperty("page_count") Integer pageCount,
        @JsonProperty("device_file_uri") String deviceFileUri,
        @JsonProperty("file_size_mb") Double fileSizeMb,
        @JsonProperty("status") String status,
        @JsonProperty("uploaded_at") Instant uploadedAt,
        @JsonProperty("processed_at") Instant processedAt,
        @JsonProperty("error_message") String errorMessage
) {
}
