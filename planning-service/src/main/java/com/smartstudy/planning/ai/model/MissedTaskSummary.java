package com.smartstudy.planning.ai.model;

import java.util.List;
import java.util.UUID;

public record MissedTaskSummary(
    int missedCount,
    int escalatedCount,
    boolean requiresFullReschedule,
    List<UUID> missedTaskIds,
    long daysToExam
) {}
