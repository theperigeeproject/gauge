package pl.perigee.gauge.domain;

public record UpdateUserRequest(
    String username,
    UserRole role,
    boolean active
) {}