package pl.perigee.gauge.domain;

public record PatchIssueRequest(
    String title,
    String description
) {}