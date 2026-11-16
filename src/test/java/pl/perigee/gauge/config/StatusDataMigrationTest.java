package pl.perigee.gauge.config;

import pl.perigee.gauge.domain.Issue;
import pl.perigee.gauge.domain.IssueStatus;
import pl.perigee.gauge.domain.Priority;
import pl.perigee.gauge.domain.Project;
import pl.perigee.gauge.domain.StatusLevel;
import pl.perigee.gauge.domain.Task;
import pl.perigee.gauge.domain.Tasklist;
import pl.perigee.gauge.service.StatusService;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Increment 3 of 0000186F: {@code statusId} exists, and pre-migration rows get filled from the
 * legacy status code. ADR 0020 §5.
 *
 * <p>The migration is driven level by level rather than by booting the application once per
 * assertion. That is legitimate because the migration is an idempotent function over whatever rows
 * happen to be present — and the "real database, real boot" rehearsal is a separate gate.
 */
@QuarkusTest
class StatusDataMigrationTest {

    @Inject StatusDataMigrationStartup migration;
    @Inject StatusService statusService;
    @Inject EntityManager em;

    // ── The happy path, per level ───────────────────────────────────

    @Test
    @TestTransaction
    void everySeededIssueCodeMigratesToItsStatusId() {
        Issue todo = insertIssue("MIG001", "TODO");
        Issue doing = insertIssue("MIG002", "DOING");
        Issue done = insertIssue("MIG003", "DONE");
        Issue inactive = insertIssue("MIG004", "INACTIVE");
        em.flush();

        migrateAll();

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
        migrateAll();

        assertEquals(afterFirst, reload(issue.id).statusId);
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
    void aRowWithNeitherIdNorCodeIsSkippedRatherThanGuessed() {
        Issue issue = new Issue("MIG012", "PROJ0001", "t", "d", IssueStatus.TODO, Priority.NORMAL);
        issue.status = null;
        issue.statusId = null;
        em.persist(issue);
        em.flush();

        assertDoesNotThrow(this::migrateAll,
            "nothing to migrate from is not an error — it only exists for hand-written rows");
        assertNull(reload("MIG012").statusId);
    }

    // ── The failure path, which is the point of the whole rule ──────

    @Test
    @TestTransaction
    void anUnmatchedCodeAbortsAndNamesLevelValueAndCount() {
        insertIssue("MIG020", "WORKING");
        insertIssue("MIG021", "WORKING");
        em.flush();

        IllegalStateException e = assertThrows(IllegalStateException.class, this::migrateAll);

        String message = e.getMessage();
        assertTrue(message.contains("WORKING"), "must name the offending value: " + message);
        assertTrue(message.contains("issue"), "must name the level: " + message);
        assertTrue(message.contains("2"), "must name how many rows carry it: " + message);
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

    // ── Helpers ─────────────────────────────────────────────────────

    /**
     * Calls the migration phases directly, inside the test's own transaction.
     *
     * <p>Deliberately NOT wrapped in {@code QuarkusTransaction.run}: that opens a separate
     * transaction which cannot see this test's uncommitted rows, so every phase would silently find
     * zero rows and pass for the wrong reason.
     */
    private void migrateAll() {
        migration.migrateIssues();
        migration.migrateTasklists();
        migration.migrateTasks();
        migration.migrateProjects();
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
