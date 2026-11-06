package pl.perigee.gauge.domain;

public record CreateTaskRequest(
    String title,
    String description
) {}