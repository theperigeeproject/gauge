package com.sheahorn.gauge.service;

import com.sheahorn.gauge.domain.ConflictException;
import com.sheahorn.gauge.domain.PatchStatusRequest;
import com.sheahorn.gauge.domain.StatusCategory;
import com.sheahorn.gauge.domain.StatusDefaults;
import com.sheahorn.gauge.domain.StatusDefinition;
import com.sheahorn.gauge.domain.StatusLevel;
import com.sheahorn.gauge.domain.ValidationException;
import com.sheahorn.gauge.repository.IssueRepository;
import com.sheahorn.gauge.repository.StatusDefinitionRepository;
import com.sheahorn.gauge.repository.TaskRepository;
import com.sheahorn.gauge.repository.TasklistRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Mutations on the status registry. Admin-only at the resource layer.
 *
 * <p>Invariants enforced here:
 * <ul>
 *   <li>codes are unique per level and look like codes;</li>
 *   <li>a level always keeps at least one ACTIVE status, and the start status {@code TODO}
 *       always exists — otherwise new entities could not be created or analysed sanely;</li>
 *   <li>renaming rewrites existing rows in the same transaction;</li>
 *   <li>deleting an in-use status requires an explicit reassignment target.</li>
 * </ul>
 */
@ApplicationScoped
public class StatusService {

    private static final Logger LOG = Logger.getLogger(StatusService.class);
    private static final Pattern CODE = Pattern.compile("[A-Z0-9_]{2,32}");
    private static final Pattern COLOR = Pattern.compile("#[0-9a-fA-F]{6}");

    @Inject
    StatusDefinitionRepository repository;

    @Inject
    StatusRegistry registry;

    @Inject
    IssueRepository issueRepository;

    @Inject
    TasklistRepository tasklistRepository;

    @Inject
    TaskRepository taskRepository;

    public List<StatusDefinition> list(StatusLevel level) {
        return registry.definitions(level);
    }

    public List<StatusDefinition> listAll() {
        return repository.findAll();
    }

    @Transactional
    public StatusDefinition create(StatusLevel level, String code, String label,
                                   StatusCategory category, String color, Integer rank,
                                   Boolean showByDefault) {
        String normalized = normalizeCode(code);
        if (registry.exists(level, normalized)) {
            throw new ConflictException("STATUS_EXISTS",
                "Status '" + normalized + "' already exists for " + level.name() + ".");
        }
        if (category == null) {
            throw new ValidationException("INVALID_CATEGORY",
                "Category is required. Valid values: ACTIVE, FINISHED, PARKED.");
        }
        int resolvedRank = rank != null ? rank : nextRank(level);
        String resolvedColor = normalizeColor(color);
        StatusDefinition created = StatusDefinition.create(
            normalized,
            label != null && !label.isBlank() ? label.trim() : normalized,
            level, category, resolvedRank, resolvedColor,
            showByDefault == null || showByDefault,
            false
        );
        StatusDefinition saved = repository.save(created);
        registry.invalidate();
        return saved;
    }

    @Transactional
    public StatusDefinition patch(String id, PatchStatusRequest body) {
        if (body == null) {
            throw new ValidationException("EMPTY_BODY", "Nothing to patch.");
        }
        StatusDefinition existing = repository.findById(id).orElseThrow(() ->
            new ValidationException("STATUS_NOT_FOUND", "No status definition with id '" + id + "'."));
        StatusLevel level = existing.level();

        String newCode = existing.code;
        if (body.code() != null && !body.code().equalsIgnoreCase(existing.code)) {
            newCode = normalizeCode(body.code());
            if (registry.exists(level, newCode)) {
                throw new ConflictException("STATUS_EXISTS",
                    "Status '" + newCode + "' already exists for " + level.name() + ".");
            }
            if (StatusDefaults.START_CODE.equals(existing.code)) {
                throw new ConflictException("START_STATUS_PROTECTED",
                    "The start status '" + StatusDefaults.START_CODE + "' cannot be renamed.");
            }
        }

        StatusCategory category = body.category() != null
            ? StatusCategory.parse(body.category())
            : existing.category();
        if (category == null) {
            throw new ValidationException("INVALID_CATEGORY",
                "Invalid category '" + body.category() + "'. Valid values: ACTIVE, FINISHED, PARKED.");
        }
        if (existing.category() == StatusCategory.ACTIVE && category != StatusCategory.ACTIVE
            && activeCount(level) <= 1) {
            throw new ValidationException("LAST_ACTIVE_STATUS",
                level.name() + " must keep at least one ACTIVE status.");
        }
        if (StatusDefaults.START_CODE.equals(existing.code) && category != StatusCategory.ACTIVE) {
            throw new ConflictException("START_STATUS_PROTECTED",
                "The start status '" + StatusDefaults.START_CODE + "' must stay ACTIVE — new entities are created in it.");
        }

        StatusDefinition updated = new StatusDefinition(
            existing.id,
            newCode,
            body.label() != null && !body.label().isBlank() ? body.label().trim() : existing.label,
            level,
            category,
            body.rank() != null ? body.rank() : existing.rank,
            body.color() != null ? normalizeColor(body.color()) : existing.color,
            body.showByDefault() != null ? body.showByDefault() : existing.showByDefault,
            existing.builtin
        );

        if (!newCode.equals(existing.code)) {
            rewrite(level, existing.code, newCode);
        }
        StatusDefinition saved = repository.save(updated);
        registry.invalidate();
        return saved;
    }

    @Transactional
    public void delete(String id, String reassignToRaw) {
        StatusDefinition existing = repository.findById(id).orElseThrow(() ->
            new ValidationException("STATUS_NOT_FOUND", "No status definition with id '" + id + "'."));
        StatusLevel level = existing.level();

        if (existing.builtin) {
            throw new ConflictException("BUILTIN_STATUS",
                "'" + existing.code + "' is a built-in status and cannot be deleted. Change its label or category instead.");
        }
        if (StatusDefaults.START_CODE.equals(existing.code)) {
            throw new ConflictException("START_STATUS_PROTECTED",
                "The start status '" + StatusDefaults.START_CODE + "' cannot be deleted.");
        }
        if (existing.category() == StatusCategory.ACTIVE && activeCount(level) <= 1) {
            throw new ValidationException("LAST_ACTIVE_STATUS",
                level.name() + " must keep at least one ACTIVE status.");
        }

        long inUse = countInUse(level, existing.code);
        if (inUse > 0) {
            if (reassignToRaw == null || reassignToRaw.isBlank()) {
                throw new ConflictException("STATUS_IN_USE",
                    "'" + existing.code + "' is used by " + inUse + " " + level.name().toLowerCase()
                        + "(s). Retry with ?reassignTo=<code> to move them first.");
            }
            String target = registry.validate(level, reassignToRaw);
            if (target.equalsIgnoreCase(existing.code)) {
                throw new ValidationException("INVALID_REASSIGNMENT",
                    "Cannot reassign '" + existing.code + "' to itself.");
            }
            rewrite(level, existing.code, target);
            LOG.infof("Reassigned %d %s(s) from %s to %s", inUse,
                level.name().toLowerCase(), existing.code, target);
        }

        repository.deleteById(id);
        registry.invalidate();
    }

    public long countInUse(StatusLevel level, String code) {
        return switch (level) {
            case ISSUE -> issueRepository.countByStatus(code);
            case TASKLIST -> tasklistRepository.countByStatus(code);
            case TASK -> taskRepository.countByStatus(code);
        };
    }

    private void rewrite(StatusLevel level, String from, String to) {
        switch (level) {
            case ISSUE -> issueRepository.replaceStatus(from, to);
            case TASKLIST -> tasklistRepository.replaceStatus(from, to);
            case TASK -> taskRepository.replaceStatus(from, to);
        }
    }

    private long activeCount(StatusLevel level) {
        return registry.definitions(level).stream()
            .filter(d -> d.category() == StatusCategory.ACTIVE)
            .count();
    }

    private int nextRank(StatusLevel level) {
        return registry.definitions(level).stream()
            .mapToInt(StatusDefinition::rank)
            .max()
            .orElse(-1) + 1;
    }

    private String normalizeCode(String code) {
        if (code == null || code.isBlank()) {
            throw new ValidationException("INVALID_CODE", "Status code is required (2-32 chars, A-Z 0-9 _).");
        }
        String normalized = code.trim().toUpperCase();
        if (!CODE.matcher(normalized).matches()) {
            throw new ValidationException("INVALID_CODE",
                "Invalid status code '" + code + "'. Use 2-32 characters: A-Z, 0-9, underscore.");
        }
        return normalized;
    }

    private String normalizeColor(String color) {
        if (color == null || color.isBlank()) {
            return "#94a3b8";
        }
        String trimmed = color.trim();
        if (!COLOR.matcher(trimmed).matches()) {
            throw new ValidationException("INVALID_COLOR",
                "Invalid colour '" + color + "'. Use a hex value like #f87171.");
        }
        return trimmed.toLowerCase();
    }

    public Map<StatusLevel, List<StatusDefinition>> everything() {
        return Map.of(
            StatusLevel.ISSUE, registry.definitions(StatusLevel.ISSUE),
            StatusLevel.TASKLIST, registry.definitions(StatusLevel.TASKLIST),
            StatusLevel.TASK, registry.definitions(StatusLevel.TASK)
        );
    }
}
