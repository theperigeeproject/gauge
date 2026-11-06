package pl.perigee.gauge.domain;

public record PatchTaskRequest(
    String title,
    String description
) {}