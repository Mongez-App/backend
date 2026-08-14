package com.smartstudy.planning.dto.response;

import java.util.List;
import java.util.UUID;

public record OrgMaterialListResponse(
        UUID courseId,
        List<OrgMaterialResponse> materials,
        int total
) {
}
