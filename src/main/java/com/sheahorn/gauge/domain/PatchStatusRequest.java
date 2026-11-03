package com.sheahorn.gauge.domain;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * All fields optional; {@code null} leaves the current value alone.
 *
 * <p>Changing {@code code} renames the status and rewrites every entity row that uses it
 * in the same transaction.
 */
public record PatchStatusRequest(
    @Schema(description = "New code. Renames the status and rewrites existing entities.", example = "BLOCKED")
    String code,

    @Schema(description = "New label.")
    String label,

    @Schema(description = "New behavioural role.", enumeration = {"ACTIVE", "FINISHED", "PARKED"})
    String category,

    @Schema(description = "New hex colour, #rrggbb.")
    String color,

    @Schema(description = "New sort rank.")
    Integer rank,

    @Schema(description = "Whether the status is shown by default in the project issue list.")
    Boolean showByDefault
) {}
