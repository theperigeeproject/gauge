package com.sheahorn.gauge.domain;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

public record UpdateTasklistStatusRequest(
    @Schema(
        description = "New status. Valid values come from the configurable TASKLIST status set — "
            + "GET /api/statuses?level=TASKLIST for the current list. Defaults: TODO, DOING, DONE, CANCELED.",
        required = true
    )
    String status
) {}
