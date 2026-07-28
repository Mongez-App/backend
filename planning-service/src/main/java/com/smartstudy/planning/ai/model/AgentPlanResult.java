package com.smartstudy.planning.ai.model;

import com.smartstudy.planning.dto.response.AlertResponse;

public record AgentPlanResult(
    String status,
    AlertResponse alert
) {}
