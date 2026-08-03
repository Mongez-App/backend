package com.smartstudy.planning.model;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum Priority {
    HIGH,
    MEDIUM,
    LOW;

    @JsonCreator
    public static Priority fromString(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        for (Priority p : Priority.values()) {
            if (p.name().equalsIgnoreCase(value.trim())) {
                return p;
            }
        }
        throw new IllegalArgumentException("Invalid priority: '" + value + "'. Allowed values: HIGH, MEDIUM, LOW");
    }
}

