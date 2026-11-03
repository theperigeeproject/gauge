package com.sheahorn.gauge.domain;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

public record CreateIssueRequest(
    String title,
    String description,
    @Schema(
        description = "Priority of the issue. Defaults to NORMAL when omitted.",
        enumeration = {"HIGH", "NORMAL", "LOW"},
        defaultValue = "NORMAL"
    )
    String priority
) {}
