package com.smartstudy.planning.ai.model;

import com.smartstudy.planning.model.Priority;

import java.time.LocalDate;
import java.util.UUID;

public record ScheduledPart(
    String title,
    LocalDate date,
    int minutes,
    int sequence,
    Integer splitPart,
    Integer totalParts,
    Priority priority
) {
    public ScheduledPart(String title, LocalDate date, int minutes, int sequence, Integer splitPart, Integer totalParts) {
        this(title, date, minutes, sequence, splitPart, totalParts, Priority.MEDIUM);
    }
}