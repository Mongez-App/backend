package com.smartstudy.planning.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CreateMaterialRequest(
        @NotBlank @JsonProperty("file_name") String fileName,
        @NotBlank @JsonProperty("content_type") String contentType,
        @NotNull @Positive @JsonProperty("file_size_bytes") Long fileSizeBytes,
        @JsonProperty("page_count") Integer pageCount
) {
}
