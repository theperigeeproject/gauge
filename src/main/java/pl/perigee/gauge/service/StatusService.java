package pl.perigee.gauge.service;

import pl.perigee.gauge.domain.AttitudeTypeDef;
import pl.perigee.gauge.domain.ConflictException;
import pl.perigee.gauge.domain.IssueStatusDef;
import pl.perigee.gauge.domain.ProgressLevelDef;
import pl.perigee.gauge.domain.ProjectStatusDef;
import pl.perigee.gauge.domain.StatusBase;
import pl.perigee.gauge.domain.StatusBearing;
import pl.perigee.gauge.domain.StatusLevel;
import pl.perigee.gauge.domain.TaskStatusDef;
import pl.perigee.gauge.domain.TasklistStatusDef;
import pl.perigee.gauge.domain.ValidationException;
import pl.perigee.gauge.repository.AttitudeTypeRepository;
import pl.perigee.gauge.repository.IssueStatusRepository;
import pl.perigee.gauge.repository.ProgressLevelRepository;
import pl.perigee.gauge.repository.ProjectStatusRepository;
import pl.perigee.gauge.repository.TaskStatusRepository;
import pl.perigee.gauge.repository.TasklistStatusRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The single owner of status data. Nothing outside this class may query the four status tables or
 * the two lookups — resources, templates and other services ask here.
 *
 * <p>That rule is what makes the rest of ADR 0020 hold together:
 *
 * <ul>
 *   <li><b>Code &harr; id translation happens here and nowhere else</b> (§4). The surface speaks
 *       codes; the database stores ids. A second translation site is the two-naming-rule failure
 *       ADR 0018 already documented once.</li>
 *   <li><b>Semantics come from the two axes, never from a code</b> (§6). {@link #isTerminal},
 *       {@link #isParked} and {@link #isActive} are the only permitted way to ask "what does this
 *       status mean", so renaming a status can never change behaviour.</li>
 *   <li><b>Exclusion is one predicate</b> (§8), {@link #isReadVisible}.</li>
 * </ul>
 *
 * <h2>Caches</h2>
 * Two per level, both rebuilt from scratch on every write (§9): {@code all} and {@code available}.
 * No incremental patching and no invalidation logic — the sets are tiny and a rebuild cannot be
 * subtly wrong. They are built lazily on first read rather than at startup, so this bean has no
 * ordering dependency on {@code StatusSeedStartup}: whichever runs first, the first read sees the
 * seeded rows.
 *
 * <p><b>What "available" means.</b> A status is available when the flags currently permit touching
 * it. {@code ARCHIVE} is available only with {@code gauge.status.archive-enabled}, {@code TRASH}
 * only with {@code gauge.status.trash-enabled}, and {@code NASCENT}/{@code FINALIZED} only with
 * {@code gauge.status.extended-progress-levels}. This is the flag's own wording — it "allows using
 * (retrieving, creating, patching)" such statuses — so excluding them unconditionally would make
 * the flags pointless. Keeping archived and trashed <em>entity rows</em> out of listings is a
 * different question, answered by {@link #isReadVisible}.
 */
@ApplicationScoped
public class StatusService {

    private static final Logger LOG = Logger.getLogger(StatusService.class);

    /** The seed code a null status id resolves to. Mirrors {@code StatusSeedStartup}. */
    static final String NEEDS_MIGRATION = "NEEDS_MIGRATION";

    /**
     * The attitude that marks the fallback status. Resolved by attitude, not code, so renaming the
     * status is free (ADR 0020 §2/§6). Exactly one per level table — enforced on create and update.
     */
    static final String MIGRATION_ATTITUDE = "MIGRATION";

    // ── Flags (ADR 0020 §7). Defaults are the decided ones. ──────────
    @ConfigProperty(name = "gauge.status.extended-progress-levels", defaultValue = "false")
    boolean extendedProgressLevels;

    @ConfigProperty(name = "gauge.status.archive-enabled", defaultValue = "true")
    boolean archiveEnabled;

    @ConfigProperty(name = "gauge.status.trash-enabled", defaultValue = "false")
    boolean trashEnabled;

    @Inject ProjectStatusRepository projectStatusRepository;
    @Inject IssueStatusRepository issueStatusRepository;
    @Inject TasklistStatusRepository tasklistStatusRepository;
    @Inject TaskStatusRepository taskStatusRepository;
    @Inject ProgressLevelRepository progressLevelRepository;
    @Inject AttitudeTypeRepository attitudeTypeRepository;
    @Inject StatusReferenceScanner referenceScanner;
    @Inject jakarta.transaction.TransactionSynchronizationRegistry txSync;

    /** Guards both cache maps and both lookup maps. Small, hot, uncontended. */
    private final Object cacheLock = new Object();

    private Map<StatusLevel, List<StatusBase>> allCache;
    private Map<StatusLevel, List<StatusBase>> availableCache;
    private Map<String, Integer> progressLevels;
    private Map<String, Integer> attitudeTypes;

    // ── Caches ───────────────────────────────────────────────────────

    /** Every status row of this level, ordered by {@code precedenceValue}. */
    public List<StatusBase> all(StatusLevel level) {
        ensureLoaded();
        return allCache.get(level);
    }

    /** The flag-permitted subset of {@link #all}. */
    public List<StatusBase> available(StatusLevel level) {
        ensureLoaded();
        return availableCache.get(level);
    }

    /** {@code name -> ordinal} for the six fixed progress levels. */
    public Map<String, Integer> progressLevels() {
        ensureLoaded();
        return progressLevels;
    }

    /** {@code name -> ordinal} for the five fixed attitudes. */
    public Map<String, Integer> attitudeTypes() {
        ensureLoaded();
        return attitudeTypes;
    }

    private void ensureLoaded() {
        if (allCache != null) {
            return;
        }
        synchronized (cacheLock) {
            if (allCache == null) {
                rebuild();
            }
        }
    }

    /**
     * Rebuilds every cache from the database. Public so a write path can trigger it inside the same
     * transaction, and so a test can prove the rebuild actually happens.
     */
    public void rebuild() {
        synchronized (cacheLock) {
            Map<StatusLevel, List<StatusBase>> all = new LinkedHashMap<>();
            Map<StatusLevel, List<StatusBase>> available = new LinkedHashMap<>();
            for (StatusLevel level : StatusLevel.values()) {
                List<StatusBase> rows = freeze(loadAll(level));
                all.put(level, rows);
                available.put(level, rows.stream().filter(this::isAvailable).toList());
            }
            Map<String, Integer> levels = new LinkedHashMap<>();
            for (ProgressLevelDef l : progressLevelRepository.listAll()) {
                levels.put(l.name, l.ordinal);
            }
            Map<String, Integer> attitudes = new LinkedHashMap<>();
            for (AttitudeTypeDef a : attitudeTypeRepository.listAll()) {
                attitudes.put(a.name, a.ordinal);
            }

            this.allCache = Collections.unmodifiableMap(all);
            this.availableCache = Collections.unmodifiableMap(available);
            this.progressLevels = Collections.unmodifiableMap(levels);
            this.attitudeTypes = Collections.unmodifiableMap(attitudes);
            LOG.debugf("Status caches rebuilt: levels=%d attitudes=%d", levels.size(), attitudes.size());
        }
    }

    private static List<StatusBase> freeze(List<StatusBase> rows) {
        return Collections.unmodifiableList(new ArrayList<>(rows));
    }

    /**
     * Rebuilds now, and arranges for the cache to be discarded if the surrounding transaction does
     * not commit.
     *
     * <p>Both halves are needed, and the second is the one that is easy to miss:
     *
     * <ul>
     *   <li>Rebuilding eagerly keeps reads inside the same transaction consistent — the caller that
     *       just created a status can see it.</li>
     *   <li>Rebuilding alone is <b>wrong</b>: a cache lives on an application-scoped bean and
     *       survives a rollback, so a failed transaction would leave the cache describing rows that
     *       no longer exist. On rollback the cache is dropped instead, and the next read rebuilds
     *       from the real database.</li>
     * </ul>
     */
    private void refreshAfterWrite() {
        rebuild();
        try {
            txSync.registerInterposedSynchronization(new jakarta.transaction.Synchronization() {
                @Override
                public void beforeCompletion() {
                }

                @Override
                public void afterCompletion(int status) {
                    if (status != jakarta.transaction.Status.STATUS_COMMITTED) {
                        invalidate();
                    }
                }
            });
        } catch (IllegalStateException noActiveTransaction) {
            // No transaction to observe — the eager rebuild above is the whole story.
        }
    }

    /** Drops the caches so the next read rebuilds from the database. */
    public void invalidate() {
        synchronized (cacheLock) {
            allCache = null;
            availableCache = null;
        }
    }

    @SuppressWarnings("unchecked")
    private List<StatusBase> loadAll(StatusLevel level) {
        return switch (level) {
            case PROJECT -> (List<StatusBase>) (List<?>) projectStatusRepository.listAll();
            case ISSUE -> (List<StatusBase>) (List<?>) issueStatusRepository.listAll();
            case TASKLIST -> (List<StatusBase>) (List<?>) tasklistStatusRepository.listAll();
            case TASK -> (List<StatusBase>) (List<?>) taskStatusRepository.listAll();
        };
    }

    /** Whether the current flags permit touching this status at all. */
    public boolean isAvailable(StatusBase status) {
        return switch (status.attitudeType) {
            case "ARCHIVE" -> archiveEnabled;
            case "TRASH" -> trashEnabled;
            // MIGRATION is deliberately never gated. It is the resolution target for every row whose
            // statusId is still null, so a flag flip must not be able to make the fallback point at
            // an unavailable status — that would turn a configuration change into unrenderable rows.
            case "MIGRATION" -> true;
            default -> true;
        } && (!isExtendedProgressLevel(status.progressLevel) || extendedProgressLevels);
    }

    private static boolean isExtendedProgressLevel(String progressLevel) {
        return "NASCENT".equals(progressLevel) || "FINALIZED".equals(progressLevel);
    }

    // ── Semantics from the two axes only (§6) ───────────────────────

    /** {@code FINISHED} or {@code FINALIZED}. Terminal statuses are hidden in listings by default. */
    public boolean isTerminal(StatusBase status) {
        return "FINISHED".equals(status.progressLevel) || "FINALIZED".equals(status.progressLevel);
    }

    /** {@code EXCLUDED} — parked. Reproduces today's INACTIVE: no priority bubbling, ranks last. */
    public boolean isParked(StatusBase status) {
        return "EXCLUDED".equals(status.attitudeType);
    }

    /** Neither terminal nor parked. */
    public boolean isActive(StatusBase status) {
        return !isTerminal(status) && !isParked(status);
    }

    /**
     * Whether entity rows carrying this status may appear in reads at all (§8).
     *
     * <p>ARCHIVE and TRASH are omitted from every listing, get, search and count. The rows are
     * retained — they simply never surface. One predicate, applied at the entity read layer.
     */
    public boolean isReadVisible(StatusBase status) {
        return !"ARCHIVE".equals(status.attitudeType) && !"TRASH".equals(status.attitudeType);
    }

    // ── Code ↔ id: the only translation site (§4) ────────────────────

    /**
     * Resolves a caller-supplied code to the internal status id, for storing on an entity.
     *
     * @return the status id — internal, never returned to a caller
     * @throws ValidationException naming the valid codes when the value is unknown or disabled
     */
    public Integer resolveForWrite(StatusLevel level, String code) {
        return require(level, code).id;
    }

    /** Resolves a caller-supplied code to the status row itself. */
    public StatusBase require(StatusLevel level, String code) {
        if (code == null || code.isBlank()) {
            throw new ValidationException(
                "INVALID_STATUS",
                "Status is required. Valid values for " + level.wire + ": " + codesOf(available(level)) + "."
            );
        }
        String wanted = code.trim();
        for (StatusBase candidate : available(level)) {
            if (candidate.code.equalsIgnoreCase(wanted)) {
                return candidate;
            }
        }
        // Distinguish "no such status" from "exists but currently disabled" — otherwise a caller
        // chasing a 400 cannot tell a typo from a switched-off feature.
        for (StatusBase candidate : all(level)) {
            if (candidate.code.equalsIgnoreCase(wanted)) {
                throw new ValidationException(
                    "STATUS_NOT_AVAILABLE",
                    "Status '" + candidate.code + "' exists but is not available: "
                        + unavailableReason(candidate)
                        + " Valid values for " + level.wire + ": " + codesOf(available(level)) + "."
                );
            }
        }
        throw new ValidationException(
            "INVALID_STATUS",
            "Invalid status '" + code + "' for " + level.wire + ". Valid values: "
                + codesOf(available(level)) + "."
        );
    }

    private String unavailableReason(StatusBase status) {
        return switch (status.attitudeType) {
            case "ARCHIVE" -> "attitude ARCHIVE requires gauge.status.archive-enabled=true.";
            case "TRASH" -> "attitude TRASH requires gauge.status.trash-enabled=true.";
            default -> isExtendedProgressLevel(status.progressLevel)
                ? "progress level " + status.progressLevel
                    + " requires gauge.status.extended-progress-levels=true."
                : "it is not available.";
        };
    }

    /**
     * The surface identity of a stored status id.
     *
     * <p><b>A null id is legal and means "not migrated yet".</b> It resolves to the level's
     * {@code NEEDS_MIGRATION} status rather than throwing. That is what makes the migration optional:
     * an upgraded database whose rows have never been migrated still renders every row, correctly
     * labelled, and the application never treats its own data as corruption.
     *
     * <p>A <em>non-null</em> id that matches no row is still a real bug — an entity holding a status
     * that does not exist — and stays loud for exactly that reason.
     */
    public String codeFor(StatusLevel level, Integer id) {
        return byId(level, id).code;
    }

    /** True when this row's status was never brought over from the legacy code column. */
    public boolean isUnmigrated(Integer id) {
        return id == null;
    }

    // ── Entity-level helpers (increment 4 of 0000186F) ──────────────
    //
    // These exist so that no mapper, service or template needs its own level constant, its own
    // null-vs-id reasoning, or a repository lookup. Every one of them is a thin composition of
    // byId/precedence — the translation stays here (ADR 0020 §2/§4).

    /** The wire code of the status an entity carries. A null status id reads as NEEDS_MIGRATION. */
    public String codeOf(StatusLevel level, StatusBearing entity) {
        return codeFor(level, entity.statusId());
    }

    /** The status row an entity carries, resolved through the null-id fallback. */
    public StatusBase of(StatusLevel level, StatusBearing entity) {
        return byId(level, entity.statusId());
    }

    /**
     * Sort key for a status id. The <b>only</b> ordering source — never a code literal (ADR 0020 §6),
     * so renaming a status cannot reorder anything by accident.
     */
    public int precedenceOf(StatusLevel level, Integer statusId) {
        return byId(level, statusId).precedenceValue;
    }

    /**
     * The status a newly created row gets (ADR 0020 §13): the available status with the lowest
     * {@code precedenceValue}.
     *
     * <p>The MIGRATION status is skipped. It is by definition "this row was never given a status",
     * which is the one thing a fresh row is not — and it is seeded last precisely so it never wins
     * by precedence anyway. Skipping it makes that structural rather than incidental.
     *
     * @throws IllegalStateException when the level has no available status at all
     */
    public Integer defaultStatusId(StatusLevel level) {
        StatusBase best = null;
        for (StatusBase candidate : available(level)) {
            if (MIGRATION_ATTITUDE.equals(candidate.attitudeType)) {
                continue;
            }
            if (best == null || candidate.precedenceValue < best.precedenceValue) {
                best = candidate;
            }
        }
        if (best == null) {
            throw new IllegalStateException(
                "No available " + level.wire + " status exists, so a new row has no default status. "
                    + "Every seeded database has one; a level emptied by hand is a broken database.");
        }
        return best.id;
    }

    /**
     * Filter predicate for an already-resolved status set.
     *
     * <p>Matched <b>by identity</b>, which quietly does the right thing for unmigrated rows: a null
     * status id resolves to the level's MIGRATION status, so {@code ?status=NEEDS_MIGRATION} returns
     * exactly the unmigrated rows, while any other filter excludes them. Comparing raw codes instead
     * would return nothing for {@code NEEDS_MIGRATION} — the rows are right there in the list.
     *
     * @param wanted resolved status rows; null or empty means "no filter"
     */
    public boolean matchesFilter(StatusLevel level, Integer statusId, Set<StatusBase> wanted) {
        if (wanted == null || wanted.isEmpty()) {
            return true;
        }
        StatusBase row = byId(level, statusId);
        for (StatusBase candidate : wanted) {
            if (candidate.id != null && candidate.id.equals(row.id)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The status row stored on an entity, for reading its axes. Null resolves to
     * {@code NEEDS_MIGRATION} (see {@link #codeFor}).
     *
     * <p><b>Every caller must come through here.</b> A DTO mapper or a template that does
     * {@code repo.findById(statusId)} directly bypasses the fallback and turns a legal, unmigrated
     * row into an exception or a blank — which is the whole failure this method exists to prevent.
     */
    public StatusBase byId(StatusLevel level, Integer id) {
        if (id == null) {
            return migrationStatus(level);
        }
        for (StatusBase candidate : all(level)) {
            if (id.equals(candidate.id)) {
                return candidate;
            }
        }
        throw new IllegalStateException(
            "No " + level.wire + " status with id " + id + " exists; the referencing row is corrupt.");
    }

    /**
     * The status a null {@code statusId} resolves to for this level.
     *
     * <p><b>Found by attitude, not by code.</b> ADR 0020 §6 forbids behaviour depending on a status
     * name, and this is behaviour: if the lookup used {@code "NEEDS_MIGRATION"} as a literal, renaming
     * the status — which §2 says is free — would silently break the fallback for every unmigrated row.
     * The {@code MIGRATION} attitude is the actual identity.
     *
     * <p>Resolved from {@link #all}, never from {@link #available}: the fallback must not depend on a
     * feature flag. If no migration status exists the database is genuinely broken — every database is
     * seeded with one — so this fails loudly rather than inventing a default.
     */
    public StatusBase migrationStatus(StatusLevel level) {
        for (StatusBase candidate : all(level)) {
            if (MIGRATION_ATTITUDE.equals(candidate.attitudeType)) {
                return candidate;
            }
        }
        throw new IllegalStateException(
            "No " + level.wire + " status carries attitude " + MIGRATION_ATTITUDE + ". Every database "
                + "is seeded with one by StatusSeedStartup; if it is missing the status table was "
                + "modified outside the application, and rows without a status id cannot be resolved.");
    }

    private static String codesOf(List<StatusBase> statuses) {
        return statuses.stream().map(s -> s.code).reduce((a, b) -> a + ", " + b).orElse("");
    }

    // ── Writes ──────────────────────────────────────────────────────

    /** A status to create or update. Codes and axis names, never ids. */
    public record StatusSpec(
        String code,
        int precedenceValue,
        String color,
        String progressLevel,
        String attitudeType
    ) {}

    @Transactional
    public StatusBase create(StatusLevel level, StatusSpec spec) {
        String code = validateSpec(level, spec, null);

        for (StatusBase existing : all(level)) {
            if (existing.code.equalsIgnoreCase(code)) {
                throw new ConflictException(
                    "DUPLICATE_STATUS_CODE",
                    "A " + level.wire + " status with code '" + existing.code + "' already exists."
                );
            }
            if (existing.precedenceValue == spec.precedenceValue()) {
                throw new ConflictException(
                    "DUPLICATE_PRECEDENCE",
                    "A " + level.wire + " status already uses precedenceValue "
                        + spec.precedenceValue() + " ('" + existing.code + "')."
                );
            }
        }

        StatusBase created = instantiate(level, code, spec);
        persist(level, created);
        refreshAfterWrite();
        LOG.infof("Created %s status '%s' (%s/%s, precedence %d)", level.wire, created.code,
            created.progressLevel, created.attitudeType, created.precedenceValue);
        return created;
    }

    @Transactional
    public StatusBase update(StatusLevel level, String code, StatusSpec spec) {
        StatusBase existing = requireAnyStatus(level, code);
        String newCode = validateSpec(level, spec, existing);

        for (StatusBase other : all(level)) {
            if (!other.code.equalsIgnoreCase(existing.code)
                && other.code.equalsIgnoreCase(newCode)) {
                throw new ConflictException(
                    "DUPLICATE_STATUS_CODE",
                    "A " + level.wire + " status with code '" + other.code + "' already exists."
                );
            }
            if (!other.code.equalsIgnoreCase(existing.code)
                && other.precedenceValue == spec.precedenceValue()) {
                throw new ConflictException(
                    "DUPLICATE_PRECEDENCE",
                    "A " + level.wire + " status already uses precedenceValue "
                        + spec.precedenceValue() + " ('" + other.code + "')."
                );
            }
        }

        existing.code = newCode;
        existing.precedenceValue = spec.precedenceValue();
        existing.color = spec.color();
        existing.progressLevel = spec.progressLevel().trim().toUpperCase();
        existing.attitudeType = spec.attitudeType().trim().toUpperCase();
        if (existing instanceof IssueStatusDef i) { issueStatusRepository.save(i); }
        else if (existing instanceof TaskStatusDef t) { taskStatusRepository.save(t); }
        else if (existing instanceof TasklistStatusDef tl) { tasklistStatusRepository.save(tl); }
        else if (existing instanceof ProjectStatusDef p) { projectStatusRepository.save(p); }

        refreshAfterWrite();
        LOG.infof("Updated %s status '%s' (%s/%s, precedence %d)", level.wire, existing.code,
            existing.progressLevel, existing.attitudeType, existing.precedenceValue);
        return existing;
    }

    /** The orphan policy a removal must state (§10). */
    public enum RemovalPolicy {
        PREVENT, REMOVE, MIGRATE;

        public static RemovalPolicy fromWire(String raw) {
            if (raw == null || raw.isBlank()) {
                return null;
            }
            String value = raw.trim();
            for (RemovalPolicy policy : values()) {
                if (policy.name().equalsIgnoreCase(value)) {
                    return policy;
                }
            }
            throw new ValidationException(
                "INVALID_REMOVAL_POLICY",
                "Invalid removal policy '" + raw + "'. Valid values: prevent, remove, migrate."
            );
        }
    }

    /**
     * Removes a status, applying the caller's orphan policy.
     *
     * <p>The scan happens first and unconditionally, because the whole point of the policy is that
     * nobody deletes a status without knowing what it holds. A caller who states no policy and has
     * no referencing rows simply gets the delete — there is nothing to decide.
     *
     * @param migrateToCode target status when the policy is {@code MIGRATE}, named by code (§2)
     */
    @Transactional
    public void delete(StatusLevel level, String code, RemovalPolicy policy, String migrateToCode) {
        StatusBase status = requireAnyStatus(level, code);

        // Refused before the orphan policy is even considered. This is not the "last available
        // status" invariant — it is about null statusId having somewhere to resolve to. Deleting
        // the fallback would make every unmigrated row unrenderable, and the failure would appear
        // in a listing page, nowhere near its cause.
        if (MIGRATION_ATTITUDE.equals(status.attitudeType)) {
            throw new ConflictException(
                "MIGRATION_STATUS_PROTECTED",
                "Status '" + status.code + "' cannot be removed: it is what a " + level.wire
                    + " row without a status id resolves to. Every unmigrated row would become "
                    + "unrenderable.");
        }

        // Invariant: a level with no available status becomes impossible to create rows for, and the
        // failure would surface far from its cause. Refuse rather than allow the empty state.
        //
        // The migration fallback is excluded from the count on purpose. It is always available (it
        // must be, or null status ids become unrenderable) so counting it here would make this
        // invariant unreachable — and a level left holding nothing but "needs migration" is exactly
        // the unusable state this guard exists to prevent.
        long availableLeft = available(level).stream()
            .filter(s -> !s.code.equalsIgnoreCase(status.code))
            .filter(s -> !MIGRATION_ATTITUDE.equals(s.attitudeType))
            .count();
        if (availableLeft == 0) {
            throw new ConflictException(
                "LAST_STATUS",
                "Cannot remove '" + status.code + "': it is the last available " + level.wire
                    + " status. Create a replacement first."
            );
        }

        long referenced = referenceScanner.count(level, status);

        if (referenced > 0) {
            if (policy == null) {
                throw new ConflictException(
                    "STATUS_IN_USE",
                    "Status '" + status.code + "' is used by " + referenced + " " + level.wire
                        + " row(s). Retry with " + usablePolicies(level) + "."
                );
            }
            switch (policy) {
                case PREVENT -> throw new ConflictException(
                    "STATUS_IN_USE",
                    "Status '" + status.code + "' is used by " + referenced + " " + level.wire
                        + " row(s) and policy=prevent forbids removal."
                );
                case REMOVE -> {
                    long deleted = referenceScanner.removeAll(level, status);
                    LOG.warnf("Removed %s status '%s' and cascade-deleted %d row(s)",
                        level.wire, status.code, deleted);
                }
                case MIGRATE -> {
                    if (migrateToCode == null || migrateToCode.isBlank()) {
                        throw new ValidationException(
                            "MIGRATE_TARGET_REQUIRED",
                            "policy=migrate requires ?migrateTo=<statusCode>."
                        );
                    }
                    StatusBase target = require(level, migrateToCode);
                    if (target.code.equalsIgnoreCase(status.code)) {
                        throw new ValidationException(
                            "MIGRATE_TARGET_SELF",
                            "Cannot migrate '" + status.code + "' to itself."
                        );
                    }
                    long moved = referenceScanner.reassign(level, status, target);
                    LOG.infof("Removed %s status '%s', migrated %d row(s) to '%s'",
                        level.wire, status.code, moved, target.code);
                }
            }
        } else if (policy == RemovalPolicy.MIGRATE) {
            // Nothing to move; still validate the target so a typo cannot pass silently.
            if (migrateToCode == null || migrateToCode.isBlank()) {
                throw new ValidationException(
                    "MIGRATE_TARGET_REQUIRED",
                    "policy=migrate requires ?migrateTo=<statusCode>."
                );
            }
            require(level, migrateToCode);
        }

        deleteRow(level, status);
        refreshAfterWrite();
        LOG.infof("Removed %s status '%s'", level.wire, status.code);
    }

    /**
     * The policies a caller can actually send for this level, for the {@code STATUS_IN_USE} message.
     *
     * <p>Projects do not offer {@code remove}: deleting project rows from a status removal would
     * bypass {@code removalLock} and orphan their issues, tasklists, tasks and subprojects, so
     * {@code StatusReferenceScanner.removeAll} refuses it. Advertising a policy that is guaranteed
     * to fail is exactly the "message must tell the caller what to send" contract going wrong.
     */
    private static String usablePolicies(StatusLevel level) {
        return level == StatusLevel.PROJECT
            ? "?policy=prevent|migrate (and ?migrateTo=<code>)"
            : "?policy=prevent|remove|migrate (and ?migrateTo=<code>)";
    }

    /** Looks a status up regardless of whether the flags currently make it available. */
    public StatusBase requireAnyStatus(StatusLevel level, String code) {
        if (code == null || code.isBlank()) {
            throw new ValidationException("INVALID_STATUS", "Status code is required.");
        }
        for (StatusBase candidate : all(level)) {
            if (candidate.code.equalsIgnoreCase(code.trim())) {
                return candidate;
            }
        }
        throw new ValidationException(
            "UNKNOWN_STATUS",
            "No " + level.wire + " status with code '" + code + "'. Known codes: "
                + codesOf(all(level)) + "."
        );
    }

    /** Normalises and checks a spec; returns the canonical code. */
    private String validateSpec(StatusLevel level, StatusSpec spec, StatusBase current) {
        if (spec.code() == null || spec.code().isBlank()) {
            throw new ValidationException("INVALID_STATUS", "Status code is required.");
        }
        String code = spec.code().trim();
        if (code.length() > 40) {
            throw new ValidationException(
                "INVALID_STATUS",
                "Status code is " + code.length() + " characters; the limit is 40."
            );
        }
        if (spec.progressLevel() == null || spec.progressLevel().isBlank()) {
            throw new ValidationException("INVALID_PROGRESS_LEVEL", "progressLevel is required.");
        }
        String progressLevel = spec.progressLevel().trim().toUpperCase();
        if (!progressLevels().containsKey(progressLevel)) {
            throw new ValidationException(
                "INVALID_PROGRESS_LEVEL",
                "Invalid progressLevel '" + spec.progressLevel() + "'. Valid values: "
                    + String.join(", ", progressLevels().keySet()) + "."
            );
        }
        if (!extendedProgressLevels && isExtendedProgressLevel(progressLevel)) {
            throw new ValidationException(
                "PROGRESS_LEVEL_DISABLED",
                "Progress level " + progressLevel
                    + " requires gauge.status.extended-progress-levels=true."
            );
        }
        if (spec.attitudeType() == null || spec.attitudeType().isBlank()) {
            throw new ValidationException("INVALID_ATTITUDE_TYPE", "attitudeType is required.");
        }
        String attitude = spec.attitudeType().trim().toUpperCase();
        if (!attitudeTypes().containsKey(attitude)) {
            throw new ValidationException(
                "INVALID_ATTITUDE_TYPE",
                "Invalid attitudeType '" + spec.attitudeType() + "'. Valid values: "
                    + String.join(", ", attitudeTypes().keySet()) + "."
            );
        }
        if ("ARCHIVE".equals(attitude) && !archiveEnabled) {
            throw new ValidationException(
                "ATTITUDE_DISABLED", "Attitude ARCHIVE requires gauge.status.archive-enabled=true."
            );
        }
        if ("TRASH".equals(attitude) && !trashEnabled) {
            throw new ValidationException(
                "ATTITUDE_DISABLED", "Attitude TRASH requires gauge.status.trash-enabled=true."
            );
        }
        // Exactly one MIGRATION status per level: it IS the fallback target, so a second one would
        // make migrationStatus() ambiguous. Renaming or recolouring the existing one stays fine —
        // this only forbids a second claim on the attitude.
        if (MIGRATION_ATTITUDE.equals(attitude)) {
            for (StatusBase other : all(level)) {
                if (MIGRATION_ATTITUDE.equals(other.attitudeType)
                    && (current == null || !other.code.equalsIgnoreCase(current.code))) {
                    throw new ConflictException(
                        "DUPLICATE_MIGRATION_STATUS",
                        "A " + level.wire + " status with attitude MIGRATION already exists ('"
                            + other.code + "'). A level has exactly one."
                    );
                }
            }
        }
        // The reverse hazard: moving the fallback status to another attitude would leave no
        // MIGRATION status behind, and every unmigrated row would throw on the next read — far from
        // the edit that caused it.
        if (current != null && MIGRATION_ATTITUDE.equals(current.attitudeType)
            && !MIGRATION_ATTITUDE.equals(attitude)) {
            throw new ConflictException(
                "MIGRATION_STATUS_PROTECTED",
                "Status '" + current.code + "' carries attitude MIGRATION and cannot be reassigned to "
                    + "another attitude: it is what a " + level.wire + " row without a status id "
                    + "resolves to."
            );
        }
        return code;
    }

    private static StatusBase instantiate(StatusLevel level, String code, StatusSpec spec) {
        String progressLevel = spec.progressLevel().trim().toUpperCase();
        String attitude = spec.attitudeType().trim().toUpperCase();
        return switch (level) {
            case PROJECT -> new ProjectStatusDef(code, spec.precedenceValue(), spec.color(),
                progressLevel, attitude);
            case ISSUE -> new IssueStatusDef(code, spec.precedenceValue(), spec.color(),
                progressLevel, attitude);
            case TASKLIST -> new TasklistStatusDef(code, spec.precedenceValue(), spec.color(),
                progressLevel, attitude);
            case TASK -> new TaskStatusDef(code, spec.precedenceValue(), spec.color(),
                progressLevel, attitude);
        };
    }

    private void persist(StatusLevel level, StatusBase status) {
        switch (level) {
            case PROJECT -> projectStatusRepository.save((ProjectStatusDef) status);
            case ISSUE -> issueStatusRepository.save((IssueStatusDef) status);
            case TASKLIST -> tasklistStatusRepository.save((TasklistStatusDef) status);
            case TASK -> taskStatusRepository.save((TaskStatusDef) status);
        }
    }

    private void deleteRow(StatusLevel level, StatusBase status) {
        switch (level) {
            case PROJECT -> projectStatusRepository.delete((ProjectStatusDef) status);
            case ISSUE -> issueStatusRepository.delete((IssueStatusDef) status);
            case TASKLIST -> tasklistStatusRepository.delete((TasklistStatusDef) status);
            case TASK -> taskStatusRepository.delete((TaskStatusDef) status);
        }
    }

    // ── Introspection used by tests and by the admin surface ────────

    public Optional<StatusBase> find(StatusLevel level, String code) {
        return all(level).stream().filter(s -> s.code.equalsIgnoreCase(code)).findFirst();
    }
}
