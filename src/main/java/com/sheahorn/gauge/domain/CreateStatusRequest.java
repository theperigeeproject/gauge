package com.sheahorn.gauge.domain;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

public record CreateStatusRequest(
    @Schema(description = "Status code written into the entity's status field. Uppercase A-Z, 0-9 and underscore, 2-32 chars.",
        required = true, example = "BLOCKED")
    String code,

    @Schema(description = "Human-readable label.", example = "Blocked")
    String label,

    @Schema(description = "Entity level this status belongs to.", enumeration = {"ISSUE", "TASKLIST", "TASK"}, required = true)
    String level,

    @Schema(description = "Behavioural role. ACTIVE bubbles priority, FINISHED counts as done, PARKED is ignored.",
        enumeration = {"ACTIVE", "FINISHED", "PARKED"}, required = true)
    String category,

    @Schema(description = "Hex colour used for the pill, #rrggbb.", example = "#f87171")
    String color,

    @Schema(description = "Sort order. Lower ranks first; ties break by code.")
    Integer rank,

    @Schema(description = "Whether the status is shown by default in the project issue list. Defaults to true.")
    Boolean showByDefault
) {}
