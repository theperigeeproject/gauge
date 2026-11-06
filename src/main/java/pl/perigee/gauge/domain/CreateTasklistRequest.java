package pl.perigee.gauge.domain;

public record CreateTasklistRequest(
    String title,
    String decomposesTaskId
) {}