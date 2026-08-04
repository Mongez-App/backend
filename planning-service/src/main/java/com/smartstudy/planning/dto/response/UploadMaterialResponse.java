package com.smartstudy.planning.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

public record UploadMaterialResponse(
        @JsonProperty("material_id") UUID materialId,
        @JsonProperty("status") String status,
        @JsonProperty("message") String message
) {
}
