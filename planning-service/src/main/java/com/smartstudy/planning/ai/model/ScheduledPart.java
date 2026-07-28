package com.smartstudy.planning.ai.model;

import java.time.LocalDate;
import java.util.UUID;

public record ScheduledPart(
    String title,
    LocalDate date,
    int minutes,
    int sequence,
    Integer splitPart,
    Integer totalParts
) {}
