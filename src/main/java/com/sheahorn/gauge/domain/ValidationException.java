package com.sheahorn.gauge.domain;

/**
 * A caller-supplied value is unacceptable. Always surfaces as HTTP 400 with a
 * machine-readable {@link #error} code and a human/LLM-readable {@link #getMessage()}.
 */
public class ValidationException extends RuntimeException {

    private final String error;

    public ValidationException(String error, String message) {
        super(message);
        this.error = error;
    }

    public String error() {
        return error;
    }
}
