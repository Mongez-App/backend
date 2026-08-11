package com.smartstudy.planning.ai.model;

import com.smartstudy.planning.model.Priority;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record ScheduledPart(
        String title,
        LocalDate date,
        int minutes,
        int sequence,
        Integer splitPart,
        Integer totalParts,
        String description,
        List<String> coveredSections,
        Priority priority
) {
    public ScheduledPart(String title, LocalDate date, int minutes, int sequence, Integer splitPart, Integer totalParts) {
        this(title, date, minutes, sequence, splitPart, totalParts, null, List.of(), Priority.MEDIUM);
    }

    public ScheduledPart(String title, LocalDate date, int minutes, int sequence, Integer splitPart, Integer totalParts, String description, List<String> coveredSections) {
        this(title, date, minutes, sequence, splitPart, totalParts, description, coveredSections, Priority.MEDIUM);
    }
}