package pl.perigee.gauge.domain;

/**
 * The request is well-formed and the caller is allowed, but the current state forbids it — a
 * {@code 409} with a machine-readable code.
 *
 * <p>Distinct from {@link ValidationException} on purpose: a validation failure means "fix your
 * payload", a conflict means "the payload is fine, the world disagrees". Mapping both to 400 would
 * tell a caller to change something that is already correct.
 *
 * <p>Mapped by {@code pl.perigee.gauge.resource.ConflictExceptionMapper}.
 */
public class ConflictException extends RuntimeException {

    private final String error;

    public ConflictException(String error, String message) {
        super(message);
        this.error = error;
    }

    public String error() {
        return error;
    }
}
