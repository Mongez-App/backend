package com.smartstudy.planning.ai.model;

import com.smartstudy.planning.dto.response.AlertResponse;

public record AgentCheckResult(
    String status,
    AlertResponse alert,
    int skippedCount,
    int conflictCount
) {
    public AgentCheckResult(String status, AlertResponse alert) {
        this(status, alert, 0, 0);
    }
}