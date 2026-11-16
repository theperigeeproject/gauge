package pl.perigee.gauge.domain;

/** Status is a code on the wire; the valid set is data, asked from StatusService per level. */
public record UpdateTaskStatusRequest(
    String status
) {}
