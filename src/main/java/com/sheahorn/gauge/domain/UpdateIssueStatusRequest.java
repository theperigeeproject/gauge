package com.sheahorn.gauge.domain;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

public record UpdateIssueStatusRequest(
    @Schema(
        description = "New status. Valid values come from the configurable ISSUE status set — "
            + "GET /api/statuses?level=ISSUE for the current list. Defaults: TODO, DOING, DONE, INACTIVE.",
        required = true
    )
    String status
) {}
