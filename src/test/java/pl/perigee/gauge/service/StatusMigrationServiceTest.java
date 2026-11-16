package pl.perigee.gauge.service;

import pl.perigee.gauge.domain.Issue;
import pl.perigee.gauge.domain.IssueStatus;
import pl.perigee.gauge.domain.Priority;
import pl.perigee.gauge.domain.Project;
import pl.perigee.gauge.domain.StatusLevel;
import pl.perigee.gauge.domain.Task;
import pl.perigee.gauge.domain.Tasklist;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The on-demand status migration (ADR 0020 §5, revision 5).
 *
 * <p>Two properties matter and they pull in opposite directions, which is why both are asserted
 * here: the happy path must actually fill {@code statusId}, and an unmatched legacy code must be
 * <b>reported</b> rather than thrown — an upgraded database must never fail to run because of a row
 * somebody hand-edited.
 *
 * <p>The migration is driven level by level rather than by booting the application once per
 * assertion. That is legitimate because each phase is an idempotent function over whatever rows are
 * present, and because it lets a phase run inside the test's own transaction — {@code
 * QuarkusTransaction.run} would open a fresh one and see nothing.
 */
@QuarkusTest
class StatusMigrationServiceTest {

    @Inject StatusMigrationService migration;
    @Inject StatusService statusService;
    @Inject EntityManager em;

    // ── It is not a startup bean, and must never become one again ────

    @Test
    void theMigrationDoesNotRunAtBoot() {
        // A startup bean would write to entity tables before Hibernate is guaranteed to have
        // exported its schema, and would make an upgraded database's first read happen through
        // whatever the bean left behind. Both hazards disappear only if nothing runs at boot.
        assertNull(StatusMigrationService.class.getAnnotation(io.quarkus.runtime.Startup.class),
            "the migration must not be @Startup — it runs on demand");
        for (Method method : StatusMigrationService.class.getDeclaredMethods()) {
            assertNull(method.getAnnotation(PostConstruct.class),
                "the migration must not run from @PostConstruct (" + method.getName() + ")");
        }
    }

    // ── The happy path, per level ───────────────────────────────────

    @Test
    @TestTransaction
    void everySeededIssueCodeMigratesToItsStatusId() {
        Issue todo = insertIssue("MIG001", "TODO");
        Issue doing = insertIssue("MIG002", "DOING");
        Issue done = insertIssue("MIG003", "DONE");
        Issue inactive = insertIssue("MIG004", "INACTIVE");
        em.flush();

        assertEquals(4, migrateAll().migrated().get("issue"));

        assertEquals(idOf(StatusLevel.ISSUE, "TODO"), reload(todo.id).statusId);
        assertEquals(idOf(StatusLevel.ISSUE, "DOING"), reload(doing.id).statusId);
        assertEquals(idOf(StatusLevel.ISSUE, "DONE"), reload(done.id).statusId);
        assertEquals(idOf(StatusLevel.ISSUE, "INACTIVE"), reload(inactive.id).statusId);
    }

    @Test
    @TestTransaction
    void tasklistsAndTasksMigrateToo() {
        Tasklist tasklist = new Tasklist("TLMIG01", "ISSUE001", "t",
            pl.perigee.gauge.domain.TasklistStatus.TODO, null);
        tasklist.status = "CANCELED"; // overwrite the constructor default with the legacy code
        em.persist(tasklist);

        Task task = new Task("TASKMIG1", "TLMIG01", 0, "t", "d",
            pl.perigee.gauge.domain.TaskStatus.TODO);
        task.status = "FAILED";
        em.persist(task);
        em.flush();

        migrateAll();

        assertEquals(idOf(StatusLevel.TASKLIST, "CANCELED"), reloadTasklist("TLMIG01").statusId);
        assertEquals(idOf(StatusLevel.TASK, "FAILED"), reloadTask("TASKMIG1").statusId);
    }

    // ── Projects: no legacy code, so a default ──────────────────────

    @Test
    @TestTransaction
    void projectsWithNoStatusGetOpened() {
        Project project = Project.create("Legacy project", "d", null);
        project.persist();
        String id = project.id;
        em.flush();

        migrateAll();

        Project reloaded = reloadProject(id);
        assertNotNull(reloaded.statusId, "a pre-existing project must get a status");
        assertEquals(idOf(StatusLevel.PROJECT, "OPENED"), reloaded.statusId);
    }

    @Test
    @TestTransaction
    void aProjectThatAlreadyHasAStatusKeepsIt() {
        Project project = Project.create("Already set", "d", null);
        project.statusId = idOf(StatusLevel.PROJECT, "CLOSED");
        project.persist();
        String id = project.id;
        em.flush();

        migrateAll();

        assertEquals(idOf(StatusLevel.PROJECT, "CLOSED"), reloadProject(id).statusId,
            "the migration must never overwrite an id that is already set");
    }

    // ── Idempotency and mixed state ─────────────────────────────────

    @Test
    @TestTransaction
    void runningTwiceChangesNothing() {
        Issue issue = insertIssue("MIG010", "DOING");
        em.flush();

        migrateAll();
        Integer afterFirst = reload(issue.id).statusId;

        StatusMigrationService.MigrationReport second = migrateAll();

        assertEquals(afterFirst, reload(issue.id).statusId);
        assertEquals(0, second.migrated().get("issue"), "nothing left to do the second time");
        assertEquals(0, second.remaining().get("issue"));
        assertTrue(second.complete());
    }

    @Test
    @TestTransaction
    void anAlreadyMigratedRowIsLeftAloneEvenIfItsLegacyCodeDisagrees() {
        Issue issue = insertIssue("MIG011", "TODO");
        issue.statusId = idOf(StatusLevel.ISSUE, "DONE");
        em.flush();

        migrateAll();

        assertEquals(idOf(StatusLevel.ISSUE, "DONE"), reload(issue.id).statusId,
            "an existing id wins — the legacy column is history, not truth");
    }

    @Test
    @TestTransaction
    void aRowWithNeitherIdNorCodeIsLeftAloneAndCountedAsRemaining() {
        Issue issue = new Issue("MIG012", "PROJ0001", "t", "d", IssueStatus.TODO, Priority.NORMAL);
        issue.status = null;
        issue.statusId = null;
        em.persist(issue);
        em.flush();

        StatusMigrationService.MigrationReport report = assertDoesNotThrow(this::migrateAll);

        assertNull(reload("MIG012").statusId,
            "nothing to migrate from is not an error, and guessing would write a status nobody chose");
        assertEquals(1, report.remaining().get("issue"));
        assertFalse(report.complete());
    }

    // ── The failure path: reported, never fatal ─────────────────────

    @Test
    @TestTransaction
    void anUnmatchedCodeIsReportedAndTheRowIsLeftUnmigrated() {
        insertIssue("MIG020", "WORKING");
        insertIssue("MIG021", "WORKING");
        em.flush();

        // The whole point of revision 5: this used to throw and abort startup.
        StatusMigrationService.MigrationReport report = assertDoesNotThrow(this::migrateAll);

        assertFalse(report.complete());
        assertEquals(1, report.unresolved().size(), "one entry per distinct value, not per row");

        StatusMigrationService.UnresolvedCode unresolved = report.unresolved().get(0);
        assertEquals("WORKING", unresolved.code(), "the value must be named");
        assertEquals("issue", unresolved.level(), "the level must be named");
        assertEquals(2, unresolved.rowCount(), "the row count must be named");

        assertNull(reload("MIG020").statusId, "the row keeps a null id so it reads as NEEDS_MIGRATION");
        assertEquals(2, report.remaining().get("issue"));
        // And the legacy column is untouched — the migration never writes it.
        assertEquals("WORKING", reload("MIG020").status);
    }

    @Test
    @TestTransaction
    void legacyCodesMatchCaseInsensitively() {
        // Legacy rows may have been written before the case-insensitive contract existed.
        Issue issue = insertIssue("MIG030", "doing");
        em.flush();

        assertDoesNotThrow(this::migrateAll);
        assertEquals(idOf(StatusLevel.ISSUE, "DOING"), reload(issue.id).statusId);
    }

    @Test
    @TestTransaction
    void theReportCoversAllFourLevels() {
        Issue issue = insertIssue("MIG040", "TODO");
        em.flush();

        StatusMigrationService.MigrationReport report = migrateAll();

        assertEquals(List.of("issue", "tasklist", "task", "project"),
            List.copyOf(report.migrated().keySet()),
            "a silent level would let rows rot without appearing in the report");
        assertEquals(1, report.migrated().get("issue"));
        assertNotNull(issue.id);
    }

    // ── Helpers ─────────────────────────────────────────────────────

    /**
     * Runs the four phases against a throwaway builder, inside the test's own transaction.
     *
     * <p>Deliberately NOT {@link StatusMigrationService#migrate()}: that wraps them in
     * {@code QuarkusTransaction.run}, which opens a separate transaction that cannot see this test's
     * uncommitted rows — every phase would find zero rows and pass for the wrong reason.
     */
    private StatusMigrationService.MigrationReport migrateAll() {
        StatusMigrationService.Builder builder = new StatusMigrationService.Builder();
        migration.migrateIssues(builder);
        migration.migrateTasklists(builder);
        migration.migrateTasks(builder);
        migration.migrateProjects(builder);
        return builder.build();
    }

    private Issue insertIssue(String id, String legacyCode) {
        Issue issue = new Issue(id, "PROJ0001", "t", "d", IssueStatus.TODO, Priority.NORMAL);
        issue.status = legacyCode;
        issue.statusId = null;
        em.persist(issue);
        return issue;
    }

    /**
     * Id of a seeded status, resolved among ALL statuses rather than the available ones.
     * CANCELED is FINALIZED, and extended progress levels are off by default, so `require()` would
     * refuse it — and the migration must not care, because it resolves history, not authoring.
     */
    private Integer idOf(StatusLevel level, String code) {
        return statusService.all(level).stream()
            .filter(s -> s.code.equals(code))
            .findFirst()
            .orElseThrow()
            .id;
    }

    private Issue reload(String id) {
        em.flush();
        em.clear();
        return Issue.findById(id);
    }

    private Tasklist reloadTasklist(String id) {
        em.flush();
        em.clear();
        return Tasklist.findById(id);
    }

    private Task reloadTask(String id) {
        em.flush();
        em.clear();
        return Task.findById(id);
    }

    private Project reloadProject(String id) {
        em.flush();
        em.clear();
        return Project.findById(id);
    }
}
