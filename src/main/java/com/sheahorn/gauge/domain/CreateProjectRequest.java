package pl.perigee.gauge.domain;

public record CreateProjectRequest(
    String name,
    String description,
    String parentId
) {}