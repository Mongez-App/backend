package com.smartstudy.planning.ai.model;

import com.smartstudy.planning.model.Priority;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * One scheduled slice of an {@link ExtractedTask}, carrying that task's
 * {@code materialId} through to the persisted row.
 */
public record ScheduledPart(
        String title,
        LocalDate date,
        int minutes,
        int sequence,
        Integer splitPart,
        Integer totalParts,
        String description,
        List<String> coveredSections,
        Priority priority,
        UUID materialId
) {
    public ScheduledPart(String title, LocalDate date, int minutes, int sequence, Integer splitPart, Integer totalParts) {
        this(title, date, minutes, sequence, splitPart, totalParts, null, List.of(), Priority.MEDIUM, null);
    }

    public ScheduledPart(String title, LocalDate date, int minutes, int sequence, Integer splitPart, Integer totalParts, String description, List<String> coveredSections) {
        this(title, date, minutes, sequence, splitPart, totalParts, description, coveredSections, Priority.MEDIUM, null);
    }

    public ScheduledPart(String title, LocalDate date, int minutes, int sequence, Integer splitPart, Integer totalParts, String description, List<String> coveredSections, Priority priority) {
        this(title, date, minutes, sequence, splitPart, totalParts, description, coveredSections, priority, null);
    }
}
