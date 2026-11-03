package com.sheahorn.gauge.service;

import com.sheahorn.gauge.domain.IssueStatus;
import com.sheahorn.gauge.domain.Priority;
import com.sheahorn.gauge.domain.ValidationException;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Parses user/agent-supplied enum-ish values into enums, and rejects unknown values with a
 * 400 that names the valid set. Never coerces, never returns null for garbage.
 */
@ApplicationScoped
public class InputParser {

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

    private static <E extends Enum<E>> String names(E[] values) {
        return Arrays.stream(values).map(Enum::name).collect(Collectors.joining(", "));
    }
}
