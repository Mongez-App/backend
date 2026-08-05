package com.smartstudy.identity.service;

import com.smartstudy.identity.dto.response.CourseTeamResponse;
import com.smartstudy.identity.dto.response.DiscoverResponse;
import com.smartstudy.identity.dto.response.JoinTeamResponse;
import com.smartstudy.identity.dto.response.TeamResponse;
import com.smartstudy.identity.dto.response.TeamSearchResponse;
import com.smartstudy.identity.dto.request.JoinTeamRequest;

import java.util.List;

public interface TeamService {
    List<TeamResponse> getUserTeams(String userId);

    DiscoverResponse getDiscoverData(String userId);

    TeamSearchResponse searchTeams(String userId, String query);

    JoinTeamResponse joinTeam(String userId, JoinTeamRequest request);

    List<CourseTeamResponse> getTeamCourses(String userId, String teamId);

    // Internal methods for Feign
    com.smartstudy.identity.model.Team getTeamById(String teamId);

    boolean isUserMember(String teamId, String userId);
}