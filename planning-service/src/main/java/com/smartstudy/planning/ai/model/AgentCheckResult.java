package com.smartstudy.planning.ai.model;

import com.smartstudy.planning.dto.response.AlertResponse;

public record AgentCheckResult(
    String status,
    AlertResponse alert
) {}
