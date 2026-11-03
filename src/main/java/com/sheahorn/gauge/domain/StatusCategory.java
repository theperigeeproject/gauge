package com.sheahorn.gauge.domain;

/**
 * The behavioural role of a status. Analysis and bubbled priority key off this, never
 * off the status code — that is what lets the codes be renamed and added freely.
 *
 * <ul>
 *   <li>{@code ACTIVE} — being worked on (or waiting). Bubbles a priority.</li>
 *   <li>{@code FINISHED} — done. Satisfies the tree; ranks above {@code PARKED}.</li>
 *   <li>{@code PARKED} — terminal but not done (parked, canceled, failed). Demands no
 *       attention: never bubbles a priority, never displaces {@code FINISHED}.</li>
 * </ul>
 */
public enum StatusCategory {
    ACTIVE,
    FINISHED,
    PARKED;

    public static StatusCategory parse(String raw) {
        if (raw == null) return null;
        String value = raw.trim();
        for (StatusCategory category : values()) {
            if (category.name().equalsIgnoreCase(value)) return category;
        }
        return null;
    }
}
