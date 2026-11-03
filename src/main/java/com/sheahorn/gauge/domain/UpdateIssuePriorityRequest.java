package com.sheahorn.gauge.domain;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

public record UpdateIssuePriorityRequest(
    @Schema(description = "New priority.", enumeration = {"HIGH", "NORMAL", "LOW"}, required = true)
    String priority
) {}
