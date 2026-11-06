package pl.perigee.gauge.domain;

public record PatchProjectRequest(
    String name,
    String description,
    String removalLock
) {}