package com.smartstudy.planning.ai.model;

public record ExtractedTask(
    String title,
    int estimatedMinutes,
    int sequenceOrder,
    String notes
) {}
