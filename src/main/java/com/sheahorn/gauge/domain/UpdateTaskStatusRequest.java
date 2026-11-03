package com.sheahorn.gauge.domain;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

public record UpdateTaskStatusRequest(
    @Schema(
        description = "New status. Valid values come from the configurable TASK status set — "
            + "GET /api/statuses?level=TASK for the current list. Defaults: TODO, DOING, DONE, FAILED, CANCELED.",
        required = true
    )
    String status
) {}
