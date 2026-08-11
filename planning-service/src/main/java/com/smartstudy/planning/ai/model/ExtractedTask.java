package com.smartstudy.planning.ai.model;

import com.smartstudy.planning.model.Priority;

import java.util.List;

public record ExtractedTask(
        String title,
        int estimatedMinutes,
        int sequenceOrder,
        String description,
        List<String> coveredSections,
        Priority priority
) {
    public ExtractedTask(String title, int estimatedMinutes, int sequenceOrder, String description, List<String> coveredSections) {
        this(title, estimatedMinutes, sequenceOrder, description, coveredSections, null);
    }

    public ExtractedTask(String title, int estimatedMinutes, int sequenceOrder, String description) {
        this(title, estimatedMinutes, sequenceOrder, description, List.of(), null);
    }
}