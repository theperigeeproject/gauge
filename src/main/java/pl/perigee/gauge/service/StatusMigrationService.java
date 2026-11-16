package pl.perigee.gauge.service;

import pl.perigee.gauge.config.StatusSeedStartup;
import pl.perigee.gauge.domain.Issue;
import pl.perigee.gauge.domain.Project;
import pl.perigee.gauge.domain.StatusBase;
import pl.perigee.gauge.domain.StatusBearing;
import pl.perigee.gauge.domain.StatusLevel;
import pl.perigee.gauge.domain.Task;
import pl.perigee.gauge.domain.Tasklist;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Fills {@code statusId} on every row that predates the reified-status model. ADR 0020 §5.
 *
 * <p><b>On demand, never at boot.</b> This is not a startup bean and must not become one again. It
 * is called from {@code POST /api/admin/statuses/migrate} or from the dashboard button. Two reasons:
 *
 * <ul>
 *   <li><b>Boot must not write to entity tables.</b> A startup bean runs before Hibernate is
 *       guaranteed to have finished its schema export, so on a real upgraded database it could query
 *       a {@code statusId} column that does not exist yet. On-demand runs strictly after boot, which
 *       makes that hazard structurally impossible instead of hoped-away.</li>
 *   <li><b>Boot must not fail on data.</b> The earlier design threw on an unmatched legacy code and
 *       aborted startup, which turned a hand-edited row into a dead application. An operator had to
 *       make another commit-and-deploy to recover. That is unacceptable; see below.</li>
 * </ul>
 *
 * <p><b>An unmatched code is reported, never fatal.</b> It is not silently swallowed either: every
 * distinct offending value appears in the returned report with its level and row count, and the rows
 * it belongs to keep a null {@code statusId}. Those rows are not lost — they render as
 * {@code NEEDS_MIGRATION} through {@link StatusService#byId}, which is what makes deferring the
 * migration entirely safe. The owner's words: "if old columns are non-null, we then think."
 *
 * <p><b>Idempotent by construction.</b> It only touches rows whose {@code statusId} is still null,
 * so pressing the button twice does nothing the second time and a partially migrated database
 * completes. That is a property of the row filter, not of a guard.
 *
 * <p><b>Seeds first.</b> Resolving a legacy code needs the seeded statuses, so the seeder is invoked
 * explicitly rather than relying on bean ordering. It is insert-only and idempotent, so calling it
 * here is free on a database that is already seeded.
 */
@ApplicationScoped
public class StatusMigrationService {

    private static final Logger LOG = Logger.getLogger(StatusMigrationService.class);

    /** The status every pre-existing project gets, since projects had no status column before. */
    static final String PROJECT_DEFAULT_CODE = "OPENED";

    @Inject StatusSeedStartup seeder;
    @Inject StatusService statusService;

    // ── The report ───────────────────────────────────────────────────

    /** One legacy value that no seeded status matched. */
    public record UnresolvedCode(String level, String code, long rowCount) {}

    /**
     * What the migration did.
     *
     * @param migrated  rows newly given a status id, per level
     * @param remaining rows still without a status id, per level
     * @param unresolved legacy values that matched nothing — the list an operator has to look at
     * @param complete   true when nothing is left unmigrated
     */
    public record MigrationReport(
        Map<String, Integer> migrated,
        Map<String, Integer> remaining,
        List<UnresolvedCode> unresolved,
        boolean complete
    ) {}

    /** Collects the per-level tallies and the unresolved list while the migration runs. */
    static final class Builder {
        private final Map<String, Integer> migrated = new LinkedHashMap<>();
        private final Map<String, Integer> remaining = new LinkedHashMap<>();
        private final List<UnresolvedCode> unresolved = new ArrayList<>();

        void level(StatusLevel level, int migratedCount, int remainingCount) {
            migrated.put(level.wire, migratedCount);
            remaining.put(level.wire, remainingCount);
        }

        void unresolved(StatusLevel level, String code, List<? extends StatusBearing> rows) {
            long affected = rows.stream()
                .filter(r -> r.statusId() == null && code != null
                    && code.equalsIgnoreCase(
                        r.legacyStatusCode() == null ? "" : r.legacyStatusCode().trim()))
                .count();
            // One entry per distinct value, not one per row — the report has to stay readable.
            for (UnresolvedCode existing : unresolved) {
                if (existing.level().equals(level.wire)
                    && existing.code().equalsIgnoreCase(code == null ? "" : code.trim())) {
                    return;
                }
            }
            unresolved.add(new UnresolvedCode(level.wire, code, affected));
        }

        MigrationReport build() {
            return new MigrationReport(migrated, remaining, unresolved, unresolved.isEmpty());
        }
    }

    // ── Entry point ──────────────────────────────────────────────────

    /**
     * Runs the whole migration in one transaction and returns what it did.
     *
     * <p>Public because the admin endpoint calls it. The per-level phases below are package-private:
     * a test drives them directly so it can run inside its own transaction, and nothing outside this
     * package may run a single phase on its own.
     */
    public MigrationReport migrate() {
        // Explicit, not by bean-ordering luck: resolving a code needs the seeds to exist.
        seeder.seed();

        Builder builder = new Builder();
        QuarkusTransaction.run(() -> {
            migrateIssues(builder);
            migrateTasklists(builder);
            migrateTasks(builder);
            migrateProjects(builder);
        });

        MigrationReport report = builder.build();
        if (report.complete()) {
            LOG.infof("Status migration complete: %s", report.migrated());
        } else {
            LOG.warnf("Status migration finished with %d unresolved legacy code(s): %s",
                report.unresolved().size(), report.unresolved());
        }
        return report;
    }

    // ── Per level ────────────────────────────────────────────────────

    void migrateIssues(Builder builder) {
        migrateLevel(StatusLevel.ISSUE, Issue.listAll(), builder);
    }

    void migrateTasklists(Builder builder) {
        migrateLevel(StatusLevel.TASKLIST, Tasklist.listAll(), builder);
    }

    void migrateTasks(Builder builder) {
        migrateLevel(StatusLevel.TASK, Task.listAll(), builder);
    }

    void migrateProjects(Builder builder) {
        // Projects had no status column at all, so there is no legacy code to resolve: every
        // pre-existing project simply becomes OPENED. If OPENED is somehow absent the projects are
        // left alone and counted as remaining rather than crashing an admin request — the database
        // is broken in a way the report can describe.
        StatusBase opened = resolve(StatusLevel.PROJECT, PROJECT_DEFAULT_CODE);
        List<Project> rows = Project.listAll();
        int migrated = 0;
        if (opened != null) {
            for (Project project : rows) {
                if (project.statusId == null) {
                    project.statusId = opened.id;
                    project.persist();
                    migrated++;
                }
            }
        } else {
            LOG.warnf("Project status '%s' is missing; %d project row(s) cannot be migrated.",
                PROJECT_DEFAULT_CODE, rows.stream().filter(p -> p.statusId == null).count());
        }
        builder.level(StatusLevel.PROJECT, migrated,
            (int) rows.stream().filter(p -> p.statusId == null).count());
    }

    /**
     * Migrates one level's rows. Generic over the four entity types through {@link StatusBearing}.
     *
     * <p>A row with neither an id nor a legacy code is left alone and counted as remaining: there is
     * nothing to migrate from, and guessing would write a status nobody chose.
     */
    private void migrateLevel(StatusLevel level, List<? extends StatusBearing> rows, Builder builder) {
        int migrated = 0;
        for (StatusBearing row : rows) {
            if (row.statusId() != null) {
                continue;
            }
            String legacy = row.legacyStatusCode();
            StatusBase match = (legacy == null || legacy.isBlank()) ? null : resolve(level, legacy);
            if (match == null) {
                // Reported, not fatal, and the row keeps a null id so it reads as NEEDS_MIGRATION.
                builder.unresolved(level, legacy, rows);
                continue;
            }
            row.setStatusId(match.id);
            persist(level, row);
            migrated++;
        }
        builder.level(level, migrated, countNulls(rows));
    }

    private static int countNulls(List<? extends StatusBearing> rows) {
        return (int) rows.stream().filter(r -> r.statusId() == null).count();
    }

    /**
     * Resolves a code against <b>all</b> statuses, deliberately ignoring the availability flags.
     *
     * <p>A migration must never depend on a feature switch. Using the available cache would mean
     * that flipping {@code gauge.status.extended-progress-levels} off could make a database that
     * legitimately contains a {@code FINALIZED} status fail to migrate — a configuration change
     * turning into a migration failure on data that was fine before. The flags govern authoring,
     * not whether history can be read.
     *
     * @return the matching status, or {@code null} when nothing matches
     */
    private StatusBase resolve(StatusLevel level, String code) {
        String wanted = code.trim();
        for (StatusBase candidate : statusService.all(level)) {
            if (candidate.code.equalsIgnoreCase(wanted)) {
                return candidate;
            }
        }
        return null;
    }

    private void persist(StatusLevel level, StatusBearing row) {
        switch (level) {
            case ISSUE -> ((Issue) row).persist();
            case TASKLIST -> ((Tasklist) row).persist();
            case TASK -> ((Task) row).persist();
            case PROJECT -> ((Project) row).persist();
        }
    }
}
