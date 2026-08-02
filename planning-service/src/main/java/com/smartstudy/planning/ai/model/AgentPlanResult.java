package com.smartstudy.planning.ai.model;

import com.smartstudy.planning.dto.response.AlertResponse;

public record AgentPlanResult(
    String status,
    AlertResponse alert,
    int skippedCount,
    int conflictCount
) {
    public AgentPlanResult(String status, AlertResponse alert) {
        this(status, alert, 0, 0);
    }
}