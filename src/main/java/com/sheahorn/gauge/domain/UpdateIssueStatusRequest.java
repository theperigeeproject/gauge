package com.sheahorn.gauge.domain;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

public record UpdateIssueStatusRequest(
    @Schema(
        description = "New status.",
        enumeration = {"TODO", "DOING", "DONE", "INACTIVE"},
        required = true
    )
    String status
) {}
