package pl.perigee.gauge.domain;

/**
 * A caller-supplied value is unacceptable. Always surfaces as HTTP 400 with a
 * machine-readable {@link #error()} code and a human/LLM-readable message.
 *
 * <p>Mapped by {@code pl.perigee.gauge.resource.ValidationExceptionMapper}. No validation
 * failure may ever reach the client as a 500, and none may be reported as success.
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
