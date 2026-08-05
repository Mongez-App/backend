package com.smartstudy.planning.service;

import com.smartstudy.planning.dto.response.CourseTeamResponse;
import com.smartstudy.planning.dto.response.TeamStatsResponse;

import java.util.List;
import java.util.UUID;

public interface TeamCourseService {
    List<CourseTeamResponse> getTeamCourses(String teamId, String userId);

    TeamStatsResponse getTeamStats(String teamId, String userId);
}