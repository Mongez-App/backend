package com.smartstudy.planning.ai.model;

import java.time.LocalDate;

public record AvailableSlot(
    LocalDate date,
    int availableMinutes
) {}
