package pl.perigee.gauge.domain;

/**
 * The four entity levels that own a status table. Not a status — a coordinate.
 *
 * <p>Four separate tables exist because a level must be free to grow a property the others do not
 * have (ADR 0020 §1). This enum is what lets one service and one admin resource address all four
 * without four copies of the same code, and it is the {@code {level}} path segment on
 * {@code /api/admin/statuses/{level}}.
 *
 * <p>The wire spelling is lower-case ({@code project}, {@code issue}, {@code tasklist}, {@code task});
 * {@link #fromWire(String)} is the only place that spelling is interpreted.
 */
public enum StatusLevel {

    PROJECT("project", "project_statuses"),
    ISSUE("issue", "issue_statuses"),
    TASKLIST("tasklist", "tasklist_statuses"),
    TASK("task", "task_statuses");

    public final String wire;
    public final String table;

    StatusLevel(String wire, String table) {
        this.wire = wire;
        this.table = table;
    }

    /** @throws ValidationException naming the accepted values when the segment is unknown */
    public static StatusLevel fromWire(String raw) {
        if (raw != null) {
            String value = raw.trim();
            for (StatusLevel level : values()) {
                if (level.wire.equalsIgnoreCase(value)) {
                    return level;
                }
            }
        }
        throw new ValidationException(
            "INVALID_STATUS_LEVEL",
            "Invalid status level '" + raw + "'. Valid values: project, issue, tasklist, task."
        );
    }
}
