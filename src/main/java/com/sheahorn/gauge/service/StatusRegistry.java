package com.sheahorn.gauge.service;

import com.sheahorn.gauge.domain.StatusCategory;
import com.sheahorn.gauge.domain.StatusDefaults;
import com.sheahorn.gauge.domain.StatusDefinition;
import com.sheahorn.gauge.domain.StatusLevel;
import com.sheahorn.gauge.domain.ValidationException;
import com.sheahorn.gauge.repository.StatusDefinitionRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * The runtime source of truth for statuses. Everything that used to switch on an enum
 * asks this instead.
 *
 * <p>Reads are cached per level. Mutations go through {@link #invalidate()}.
 *
 * <p>An unknown code is never allowed to corrupt analysis: reads fall back to
 * {@link StatusCategory#PARKED} (ignored, never counted as finished), and writes are
 * rejected outright.
 */
@ApplicationScoped
public class StatusRegistry {

    private static final Logger LOG = Logger.getLogger(StatusRegistry.class);

    @Inject
    StatusDefinitionRepository repository;

    private final Map<StatusLevel, List<StatusDefinition>> cache = new EnumMap<>(StatusLevel.class);

    public void invalidate() {
        cache.clear();
    }

    /** All definitions for a level, ordered by rank then code. Never empty. */
    public List<StatusDefinition> definitions(StatusLevel level) {
        List<StatusDefinition> cached = cache.get(level);
        if (cached != null) {
            return cached;
        }
        List<StatusDefinition> loaded;
        try {
            loaded = repository.findByLevel(level.name());
        } catch (Exception e) {
            LOG.debugf("Status table unavailable for %s (%s) — using built-in defaults", level, e.getMessage());
            loaded = List.of();
        }
        if (loaded == null || loaded.isEmpty()) {
            loaded = StatusDefaults.of(level);
        }
        List<StatusDefinition> ordered = new ArrayList<>(loaded);
        ordered.sort((a, b) -> a.rank != b.rank ? Integer.compare(a.rank, b.rank) : a.code.compareTo(b.code));
        cache.put(level, List.copyOf(ordered));
        return cache.get(level);
    }

    public List<String> codes(StatusLevel level) {
        return definitions(level).stream().map(StatusDefinition::code).toList();
    }

    public Optional<StatusDefinition> definition(StatusLevel level, String code) {
        if (code == null) return Optional.empty();
        return definitions(level).stream()
            .filter(d -> d.code.equalsIgnoreCase(code.trim()))
            .findFirst();
    }

    public boolean exists(StatusLevel level, String code) {
        return definition(level, code).isPresent();
    }

    public StatusCategory category(StatusLevel level, String code) {
        return definition(level, code)
            .map(StatusDefinition::category)
            .orElseGet(() -> {
                LOG.warnf("Unknown %s status '%s' — treating as PARKED", level, code);
                return StatusCategory.PARKED;
            });
    }

    public int rank(StatusLevel level, String code) {
        return definition(level, code).map(StatusDefinition::rank).orElse(Integer.MAX_VALUE);
    }

    public boolean showByDefault(StatusLevel level, String code) {
        return definition(level, code).map(StatusDefinition::showByDefault).orElse(true);
    }

    public String label(StatusLevel level, String code) {
        return definition(level, code).map(StatusDefinition::label).orElse(code);
    }

    public String color(StatusLevel level, String code) {
        return definition(level, code).map(StatusDefinition::color).orElse("#94a3b8");
    }

    /** Codes in this level shown by default in the project issue list. */
    public List<String> defaultVisibleCodes(StatusLevel level) {
        return definitions(level).stream()
            .filter(StatusDefinition::showByDefault)
            .map(StatusDefinition::code)
            .toList();
    }

    /**
     * Normalizes a caller-supplied code to its canonical form and rejects unknown values
     * with a 400 that names the valid set for that level.
     */
    public String validate(StatusLevel level, String raw) {
        StatusDefinition definition = definition(level, raw).orElseThrow(() -> new ValidationException(
            "INVALID_STATUS",
            raw == null || raw.isBlank()
                ? "Status is required. Valid " + level.name().toLowerCase() + " statuses: " + joined(level) + "."
                : "Invalid " + level.name().toLowerCase() + " status '" + raw + "'. Valid values: " + joined(level) + "."
        ));
        return definition.code;
    }

    private String joined(StatusLevel level) {
        return definitions(level).stream()
            .map(StatusDefinition::code)
            .collect(Collectors.joining(", "));
    }
}
