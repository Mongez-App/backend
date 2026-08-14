package com.smartstudy.planning.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record OrgMaterialResponse(
        UUID id,
        String fileName,
        String fileType,
        Integer pageCount,
        Double fileSizeMb,
        String fileUrl,
        Instant uploadedAt
) {
}
