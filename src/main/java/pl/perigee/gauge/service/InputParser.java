package pl.perigee.gauge.service;

import pl.perigee.gauge.domain.IssueStatus;
import pl.perigee.gauge.domain.Priority;
import pl.perigee.gauge.domain.ValidationException;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Parses caller-supplied enum-ish values into enums, and rejects unknown values with a 400 that
 * names the valid set. Never coerces, never returns null for garbage, and never lets a Jackson
 * enum error reach the client.
 *
 * <p>Before this existed an unknown value such as {@code priority=MEDIUM} produced an opaque
 * Jackson body ({@code {"objectName":"Class","attributeName":"priority",...}}) instead of a
 * message a human or an agent could act on — see issue 0000149B.
 *
 * <p>Placement: lives in {@code service} because it is a cross-cutting input concern; the
 * codebase has no {@code util} package, and creating one for a single class would be worse than
 * putting it next to the services that use it.
 */
@ApplicationScoped
public class InputParser {

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
     * @return the parsed status, or {@code null} when nothing was supplied
     * @throws ValidationException when a value was supplied but is not a known status
     */
    public IssueStatus issueStatus(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim();
        for (IssueStatus candidate : IssueStatus.values()) {
            if (candidate.name().equalsIgnoreCase(value)) {
                return candidate;
            }
        }
        throw new ValidationException(
            "INVALID_STATUS",
            "Invalid status '" + raw + "'. Valid values: " + names(IssueStatus.values()) + "."
        );
    }

    public IssueStatus requiredIssueStatus(String raw) {
        IssueStatus parsed = issueStatus(raw);
        if (parsed == null) {
            throw new ValidationException(
                "INVALID_STATUS",
                "Status is required. Valid values: " + names(IssueStatus.values()) + "."
            );
        }
        return parsed;
    }

    private static <E extends Enum<E>> String names(E[] values) {
        return Arrays.stream(values).map(Enum::name).collect(Collectors.joining(", "));
    }
}
