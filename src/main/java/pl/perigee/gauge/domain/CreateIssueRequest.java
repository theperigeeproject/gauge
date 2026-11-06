package pl.perigee.gauge.domain;

public record CreateIssueRequest(
    String title,
    String description,
    Priority priority
) {}