package com.sheahorn.gauge.domain;

/**
 * Which entity level a status belongs to. Status sets are configured independently
 * per level — a tasklist's {@code CANCELED} and a task's {@code FAILED} are not
 * issues' business.
 */
public enum StatusLevel {
    ISSUE,
    TASKLIST,
    TASK;

    public static StatusLevel parse(String raw) {
        if (raw == null) return null;
        String value = raw.trim();
        for (StatusLevel level : values()) {
            if (level.name().equalsIgnoreCase(value)) return level;
        }
        return null;
    }
}
