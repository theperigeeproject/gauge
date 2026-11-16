package pl.perigee.gauge.service;

import pl.perigee.gauge.domain.Priority;
import pl.perigee.gauge.domain.StatusBase;
import pl.perigee.gauge.domain.StatusLevel;
import pl.perigee.gauge.domain.ValidationException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Parses caller-supplied enum-ish values, and rejects unknown ones with a 400 that names the valid
 * set. Never coerces, never returns null for garbage, and never lets a Jackson enum error reach the
 * client.
 *
 * <p>Before this existed an unknown value such as {@code priority=MEDIUM} produced an opaque
 * Jackson body ({@code {"objectName":"Class","attributeName":"priority",...}}) instead of a
 * message a human or an agent could act on — see issue 0000149B.
 *
 * <p><b>Statuses are no longer parsed here.</b> Since increment 4 of 0000186F they are data, so the
 * vocabulary lives in {@code StatusService} and has to be asked for per level. This class is a thin
 * facade over it, which keeps the input contract (400 {@code INVALID_STATUS} naming the valid codes)
 * in one place without a second copy of the translation rule (ADR 0020 §2/§4).
 *
 * <p>Placement: lives in {@code service} because it is a cross-cutting input concern; the codebase
 * has no {@code util} package, and creating one for a single class would be worse than putting it
 * next to the services that use it.
 */
@ApplicationScoped
public class InputParser {

    @Inject
    StatusService statusService;

    /**
     * @return the parsed priority, or {@code null} when nothing was supplied (caller applies the default)
     * @throws ValidationException when a value was supplied but is not a known priority
     */
    public Priority priority(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim();
        for (Priority candidate : Priority.values()) {
            if (candidate.name().equalsIgnoreCase(value)) {
                return candidate;
            }
        }
        throw new ValidationException(
            "INVALID_PRIORITY",
            "Invalid priority '" + raw + "'. Valid values: " + names(Priority.values()) + "."
        );
    }

    public Priority requiredPriority(String raw) {
        Priority parsed = priority(raw);
        if (parsed == null) {
            throw new ValidationException(
                "INVALID_PRIORITY",
                "Priority is required. Valid values: " + names(Priority.values()) + "."
            );
        }
        return parsed;
    }

    /**
     * Resolves a caller-supplied status code for a level.
     *
     * @return the status row, or {@code null} when nothing was supplied
     * @throws ValidationException naming the valid codes when the value is unknown or flag-disabled
     */
    public StatusBase status(StatusLevel level, String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return statusService.require(level, raw);
    }

    /** Same, but a missing value is an error rather than {@code null}. */
    public StatusBase requiredStatus(StatusLevel level, String raw) {
        return statusService.require(level, raw);
    }

    private static <E extends Enum<E>> String names(E[] values) {
        return Arrays.stream(values).map(Enum::name).collect(Collectors.joining(", "));
    }
}
