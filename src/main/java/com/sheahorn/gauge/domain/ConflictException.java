package com.sheahorn.gauge.domain;

/**
 * The request was well-formed but conflicts with current state — deleting a status in use,
 * creating a duplicate code, removing a protected built-in. Surfaces as HTTP 409.
 */
public class ConflictException extends ValidationException {

    public ConflictException(String error, String message) {
        super(error, message);
    }
}
