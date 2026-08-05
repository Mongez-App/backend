package com.smartstudy.identity.client;

import com.smartstudy.identity.dto.response.CourseTeamResponse;
import com.smartstudy.identity.dto.response.TeamStatsResponse;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class TeamServiceClientFallbackFactory implements FallbackFactory<TeamServiceClient> {
    @Override
    public TeamServiceClient create(Throwable cause) {
        return new TeamServiceClient() {
            @Override
            public List<CourseTeamResponse> getTeamCourses(String teamId, String userId) {
                return List.of();
            }

            @Override
            public TeamStatsResponse getTeamStats(String teamId, String userId) {
                return new TeamStatsResponse(0.0, List.of());
            }
        };
    }
}