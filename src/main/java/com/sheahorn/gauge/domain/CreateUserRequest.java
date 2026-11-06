package pl.perigee.gauge.domain;

public record CreateUserRequest(
    String username,
    UserRole role
) {}