package com.smartstudy.identity.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum WeekDay {
    Mon("Mon"),
    Tue("Tue"),
    Wed("Wed"),
    Thu("Thu"),
    Fri("Fri"),
    Sat("Sat"),
    Sun("Sun");

    private final String value;

    WeekDay(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static WeekDay fromValue(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        for (WeekDay day : WeekDay.values()) {
            if (day.value.equalsIgnoreCase(text) || day.name().equalsIgnoreCase(text)) {
                return day;
            }
        }
        String normalized = text.trim().toLowerCase();
        if (normalized.startsWith("mon")) return Mon;
        if (normalized.startsWith("tue")) return Tue;
        if (normalized.startsWith("wed")) return Wed;
        if (normalized.startsWith("thu")) return Thu;
        if (normalized.startsWith("fri")) return Fri;
        if (normalized.startsWith("sat")) return Sat;
        if (normalized.startsWith("sun")) return Sun;

        throw new IllegalArgumentException("Invalid weekday: " + text);
    }
}
