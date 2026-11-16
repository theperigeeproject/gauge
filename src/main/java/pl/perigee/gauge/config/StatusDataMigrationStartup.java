package pl.perigee.gauge.config;

import pl.perigee.gauge.domain.Issue;
import pl.perigee.gauge.domain.Project;
import pl.perigee.gauge.domain.StatusBase;
import pl.perigee.gauge.domain.StatusBearing;
import pl.perigee.gauge.domain.StatusLevel;
import pl.perigee.gauge.domain.Task;
import pl.perigee.gauge.domain.Tasklist;
import pl.perigee.gauge.service.StatusService;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.List;

/**
 * Fills {@code statusId} on every row that predates the reified-status model. ADR 0020 §5.
 *
 * <p><b>Runs after seeding.</b> It resolves legacy codes against the seeded statuses, so the seed
 * must exist first. Rather than depend on bean ordering, this class injects
 * {@link StatusSeedStartup} and asks it to seed — the seeder is idempotent by construction, so
 * calling it here is free on a database that is already seeded and correct on one that is not.
 *
 * <p><b>An unmatched legacy code aborts startup.</b> Not a warning, not a parking value. The
 * application seeds its own defaults, so every code in a database that ever ran this application is
 * one of them; anything else means someone wrote to the table by hand and the data is not what the
 * code believes. The exception names the level, the offending value and how many rows carry it,
 * because that is everything needed to fix it without guessing.
 *
 * <p><b>Idempotent by construction.</b> It only touches rows whose {@code statusId} is still null,
 * so a second boot does nothing and a partially migrated database completes.
 *
 * <p>Shape: {@code @Startup} + {@code @PostConstruct} + {@code QuarkusTransaction.run}. That is the
 * combination that works on Quarkus 3.x in this codebase; {@code @Observes StartupEvent} +
 * {@code @Transactional} does not (issue 0000055D).
 */
@Startup
@ApplicationScoped
public class StatusDataMigrationStartup {

    private static final Logger LOG = Logger.getLogger(StatusDataMigrationStartup.class);

    /** The status every pre-existing project gets, since projects had no status column before. */
    static final String PROJECT_DEFAULT_CODE = "OPENED";

    @Inject StatusSeedStartup seeder;
    @Inject StatusService statusService;

    @PostConstruct
    void init() {
        // Makes the ordering explicit instead of hoping CDI got it right.
        seeder.seed();
        QuarkusTransaction.run(() -> {
            migrateIssues();
            migrateTasklists();
            migrateTasks();
            migrateProjects();
        });
    }

    // Package-private rather than private: the test drives each level directly instead of booting
    // the application once per assertion. Not public — nothing outside this package may run a
    // migration phase on its own.
    void migrateIssues() {
        List<Issue> rows = Issue.listAll();
        int migrated = migrate(StatusLevel.ISSUE, rows, "issues");
        logIfAny(StatusLevel.ISSUE, migrated, rows.size());
    }

    void migrateTasklists() {
        List<Tasklist> rows = Tasklist.listAll();
        int migrated = migrate(StatusLevel.TASKLIST, rows, "tasklists");
        logIfAny(StatusLevel.TASKLIST, migrated, rows.size());
    }

    void migrateTasks() {
        List<Task> rows = Task.listAll();
        int migrated = migrate(StatusLevel.TASK, rows, "tasks");
        logIfAny(StatusLevel.TASK, migrated, rows.size());
    }

    void migrateProjects() {
        // Projects had no status column at all, so there is no legacy code to resolve: every
        // pre-existing project simply becomes OPENED.
        StatusBase opened = resolveAmongAll(StatusLevel.PROJECT, PROJECT_DEFAULT_CODE, "projects");
        List<Project> rows = Project.listAll();
        int migrated = 0;
        for (Project project : rows) {
            if (project.statusId == null) {
                project.statusId = opened.id;
                project.persist();
                migrated++;
            }
        }
        logIfAny(StatusLevel.PROJECT, migrated, rows.size());
    }

    /**
     * Migrates one level's rows. Generic over the four entity types through {@link StatusBearing}.
     *
     * @return how many rows were written
     */
    private int migrate(StatusLevel level, List<? extends StatusBearing> rows, String tableName) {
        int migrated = 0;
        for (StatusBearing row : rows) {
            if (row.statusId() != null) {
                continue;
            }
            String legacy = row.legacyStatusCode();
            if (legacy == null || legacy.isBlank()) {
                // No id and no code: nothing to migrate from. Leave it null; the API rejects null
                // on write, and this state only exists for rows created directly in SQL.
                continue;
            }
            StatusBase status = resolveOrAbort(level, legacy, rows, tableName);
            row.setStatusId(status.id);
            persist(level, row);
            migrated++;
        }
        return migrated;
    }

    /**
     * Resolves a code against **all** statuses, deliberately ignoring the availability flags.
     *
     * <p>A migration must never depend on a feature switch. If it used the available cache, then
     * flipping `gauge.status.extended-progress-levels` off would make a database that legitimately
     * contains a `FINALIZED` status refuse to migrate — a configuration change turning into a boot
     * failure on data that was fine before. The flags govern authoring, not whether existing rows
     * can be understood.
     */
    private StatusBase resolveAmongAll(StatusLevel level, String code, String tableName) {
        return statusService.all(level).stream()
            .filter(s -> s.code.equalsIgnoreCase(code.trim()))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException(String.format(
                "Cannot migrate %s: no %s status has code '%s'. The seeds should always provide it; "
                    + "if it is missing, the status table was modified outside the application.",
                tableName, level.wire, code)));
    }

    private StatusBase resolveOrAbort(StatusLevel level, String legacyCode,
                                     List<? extends StatusBearing> rows, String tableName) {
        StatusBase match = statusService.all(level).stream()
            .filter(s -> s.code.equalsIgnoreCase(legacyCode.trim()))
            .findFirst()
            .orElse(null);
        if (match != null) {
            return match;
        }
        long affected = rows.stream()
            .filter(r -> r.statusId() == null
                && legacyCode.equalsIgnoreCase(
                    r.legacyStatusCode() == null ? "" : r.legacyStatusCode().trim()))
            .count();
        throw new IllegalStateException(String.format(
            "Cannot migrate %s: status code '%s' matches no seeded %s status, and %d row(s) carry it. "
                + "Every code this application ever wrote is seeded; an unrecognised one means the "
                + "table was edited outside the application. Fix or remove those rows, or seed the "
                + "missing status, then start again.",
            tableName, legacyCode, level.wire, affected));
    }

    private void persist(StatusLevel level, StatusBearing row) {
        switch (level) {
            case ISSUE -> ((Issue) row).persist();
            case TASKLIST -> ((Tasklist) row).persist();
            case TASK -> ((Task) row).persist();
            case PROJECT -> ((Project) row).persist();
        }
    }

    private static void logIfAny(StatusLevel level, int migrated, int total) {
        if (migrated > 0) {
            LOG.infof("Migrated %d of %d %s row(s) to the reified status model",
                migrated, total, level.wire);
        } else {
            LOG.debugf("No %s rows needed status migration (%d checked)", level.wire, total);
        }
    }
}
