package com.smartstudy.planning.dto.response;

import java.util.List;

public record OrgCourseListResponse(
        String teamId,
        List<OrgCourseResponse> courses,
        int total
) {
}
