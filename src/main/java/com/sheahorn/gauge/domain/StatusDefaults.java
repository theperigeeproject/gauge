package com.sheahorn.gauge.domain;

import java.util.List;

/**
 * The built-in status sets — the enums this feature replaced, expressed as data.
 *
 * <p>Used for two things: seeding {@code status_definitions} on first boot, and as an
 * in-memory fallback when the table is unavailable (e.g. Hibernate DDL set to
 * {@code none} on a fresh database). The application must never be unable to answer
 * "what statuses exist".
 *
 * <p>Colours reproduce the pre-feature palette exactly, so nothing changes visually
 * until an admin edits it.
 */
public final class StatusDefaults {

    /** Every entity is created in this status. Built-in and protected. */
    public static final String START_CODE = "TODO";

    private StatusDefaults() {
    }

    public static List<StatusDefinition> of(StatusLevel level) {
        return switch (level) {
            case ISSUE -> List.of(
                def("TODO", "Todo", level, StatusCategory.ACTIVE, 0, "#60a5fa", true),
                def("DOING", "Doing", level, StatusCategory.ACTIVE, 1, "#fbbf24", true),
                def("DONE", "Done", level, StatusCategory.FINISHED, 2, "#4ade80", false),
                def("INACTIVE", "Inactive", level, StatusCategory.PARKED, 3, "#b8b0dc", true)
            );
            case TASKLIST -> List.of(
                def("TODO", "Todo", level, StatusCategory.ACTIVE, 0, "#60a5fa", true),
                def("DOING", "Doing", level, StatusCategory.ACTIVE, 1, "#fbbf24", true),
                def("DONE", "Done", level, StatusCategory.FINISHED, 2, "#4ade80", true),
                def("CANCELED", "Canceled", level, StatusCategory.PARKED, 3, "#a78bfa", true)
            );
            case TASK -> List.of(
                def("TODO", "Todo", level, StatusCategory.ACTIVE, 0, "#60a5fa", true),
                def("DOING", "Doing", level, StatusCategory.ACTIVE, 1, "#fbbf24", true),
                def("DONE", "Done", level, StatusCategory.FINISHED, 2, "#4ade80", true),
                def("FAILED", "Failed", level, StatusCategory.PARKED, 3, "#f87171", true),
                def("CANCELED", "Canceled", level, StatusCategory.PARKED, 4, "#a78bfa", true)
            );
        };
    }

    private static StatusDefinition def(String code, String label, StatusLevel level,
                                         StatusCategory category, int rank, String color,
                                         boolean showByDefault) {
        // id is assigned by the id provider at seed time; these are transient templates.
        return new StatusDefinition(null, code, label, level, category, rank, color, showByDefault, true);
    }
}
