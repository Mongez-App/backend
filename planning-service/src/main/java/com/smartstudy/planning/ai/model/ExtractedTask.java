package com.smartstudy.planning.ai.model;

import com.smartstudy.planning.model.Priority;

public record ExtractedTask(
    String title,
    int estimatedMinutes,
    int sequenceOrder,
    String notes,
    Priority priority
) {
    public ExtractedTask(String title, int estimatedMinutes, int sequenceOrder, String notes) {
        this(title, estimatedMinutes, sequenceOrder, notes, null);
    }
}