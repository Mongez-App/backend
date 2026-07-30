package com.smartstudy.planning.model;

import java.util.Locale;
import java.util.Optional;

public enum EventType {
    QUIZ,
    ASSIGNMENT,
    PROJECT,
    MIDTERM,
    EXAM;

    /** The lowercase form used on the wire and persisted in events.event_type. */
    public String wireValue() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Human-readable label used in user-facing messages, e.g. "Midterm". */
    public String label() {
        return name().charAt(0) + name().substring(1).toLowerCase(Locale.ROOT);
    }

    public static Optional<EventType> fromWireValue(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(valueOf(value.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public static String allowedValues() {
        StringBuilder sb = new StringBuilder();
        for (EventType type : values()) {
            if (!sb.isEmpty()) {
                sb.append(", ");
            }
            sb.append(type.wireValue());
        }
        return sb.toString();
    }
}
