package com.smartstudy.planning.ai.model;

import com.smartstudy.planning.model.Priority;

import java.util.List;
import java.util.UUID;

/**
 * A unit of study work the agent has extracted or is re-planning.
 * <p>
 * {@code materialId} rides along so a task keeps pointing at the PDF it came
 * from across a reschedule — deleting a material deletes its tasks by that
 * link, and a task that loses it survives the delete as an orphan.
 * </p>
 */
public record ExtractedTask(
        String title,
        int estimatedMinutes,
        int sequenceOrder,
        String description,
        List<String> coveredSections,
        Priority priority,
        UUID materialId
) {
    public ExtractedTask(String title, int estimatedMinutes, int sequenceOrder, String description, List<String> coveredSections, Priority priority) {
        this(title, estimatedMinutes, sequenceOrder, description, coveredSections, priority, null);
    }

    public ExtractedTask(String title, int estimatedMinutes, int sequenceOrder, String description, List<String> coveredSections) {
        this(title, estimatedMinutes, sequenceOrder, description, coveredSections, null, null);
    }

    public ExtractedTask(String title, int estimatedMinutes, int sequenceOrder, String description) {
        this(title, estimatedMinutes, sequenceOrder, description, List.of(), null, null);
    }

    public ExtractedTask withMaterialId(UUID materialId) {
        return new ExtractedTask(title, estimatedMinutes, sequenceOrder, description,
                coveredSections, priority, materialId);
    }
}
