package com.smartstudy.planning.ai.model;

import java.util.UUID;

public record AgentContext(
    String userId,
    UUID courseId,
    UUID materialId,
    int dailyStudyMinutes,
    String preferredDays
) {}
