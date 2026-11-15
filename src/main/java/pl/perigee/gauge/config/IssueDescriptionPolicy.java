package pl.perigee.gauge.config;

import pl.perigee.gauge.domain.ValidationException;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Single source of truth for the issue-description length contract.
 *
 * <p>{@code gauge.issue.description.allow-long=false} (default) caps descriptions at
 * {@value #DEFAULT_MAX_LENGTH} characters. Setting it to {@code true} lifts the cap to
 * {@value #HARD_MAX_LENGTH}, which is the width of the underlying database column.
 *
 * <p>Both the create and the update path MUST go through {@link #validate(String)}, so the
 * threshold and the failure mode can never diverge again. Before this existed the cap lived
 * only in the column definition: an over-long description surfaced as a 500 with an H2 stack
 * trace on both POST and PATCH.
 */
@ApplicationScoped
public class IssueDescriptionPolicy {

    public static final int DEFAULT_MAX_LENGTH = 4000;
    public static final int HARD_MAX_LENGTH = 1_000_000;

    @ConfigProperty(name = "gauge.issue.description.allow-long", defaultValue = "false")
    boolean allowLong;

    public boolean allowLong() {
        return allowLong;
    }

    public int maxLength() {
        return allowLong ? HARD_MAX_LENGTH : DEFAULT_MAX_LENGTH;
    }

    /**
     * Rejects a description longer than the limit in force.
     * A {@code null} description means "no description", which is always allowed.
     */
    public void validate(String description) {
        if (description == null) {
            return;
        }
        int max = maxLength();
        if (description.length() > max) {
            throw new ValidationException(
                "DESCRIPTION_TOO_LONG",
                "Description is " + description.length() + " characters long; the limit is " + max
                    + (allowLong
                        ? " (hard database limit)."
                        : ". Raise it with gauge.issue.description.allow-long=true.")
            );
        }
    }
}
