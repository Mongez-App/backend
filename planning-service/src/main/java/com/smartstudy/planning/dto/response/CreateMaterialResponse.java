package com.smartstudy.planning.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

public record CreateMaterialResponse(
        @JsonProperty("material_id") UUID materialId,
        @JsonProperty("upload_url") String uploadUrl,
        @JsonProperty("alert") AlertResponse alert
) {
}
