package pl.perigee.gauge.service;

import pl.perigee.gauge.domain.Issue;
import pl.perigee.gauge.domain.Priority;
import pl.perigee.gauge.domain.StatusBase;
import pl.perigee.gauge.domain.StatusLevel;
import pl.perigee.gauge.domain.Task;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Increment 4 of 0000186F, step 1: the entity-level helpers that let everything else stop
 * reasoning about statuses itself.
 *
 * <p>Everything here is about the two properties the switchover depends on: a null status id is
 * <b>legal</b> and resolves to NEEDS_MIGRATION, and ordering/filtering come from the status rows
 * rather than from a code literal.
 */
@QuarkusTest
class StatusHelpersTest {

    @Inject
    StatusService service;

    // ── A null status id is legal ───────────────────────────────────

    @Test
    @TestTransaction
    void anUnmigratedRowReadsAsNeedsMigrationInsteadOfThrowing() {
        // null statusId is the point of this test: a legal, unmigrated row.
        Issue issue = Issue.create("PROJ1000", "unmigrated", "d", Priority.NORMAL, null);
        issue.persist();
        assertNull(issue.statusId, "the factory leaves the id null until a service sets it");

        assertEquals("NEEDS_MIGRATION", service.codeOf(StatusLevel.ISSUE, issue));
        assertEquals("MIGRATION", service.of(StatusLevel.ISSUE, issue).attitudeType,
            "the fallback is resolved by attitude, so renaming the status is free");
    }

    @Test
    @TestTransaction
    void aNonNullUnknownIdIsStillLoud() {
        Issue issue = Issue.create("PROJ1001", "corrupt", "d", Priority.NORMAL, null);
        issue.persist();
        issue.statusId = 999_999;

        // A row pointing at a status that does not exist is corruption, not "unmigrated": the
        // fallback must not swallow it.
        assertThrows(IllegalStateException.class, () -> service.codeOf(StatusLevel.ISSUE, issue));
    }

    // ── Precedence is the only ordering source ──────────────────────

    @Test
    void precedenceFollowsTheSeededOrder() {
        assertEquals(0, service.precedenceOf(StatusLevel.ISSUE, id("TODO", StatusLevel.ISSUE)));
        assertEquals(1, service.precedenceOf(StatusLevel.ISSUE, id("DOING", StatusLevel.ISSUE)));
        assertEquals(2, service.precedenceOf(StatusLevel.ISSUE, id("DONE", StatusLevel.ISSUE)));
        assertEquals(3, service.precedenceOf(StatusLevel.ISSUE, id("INACTIVE", StatusLevel.ISSUE)));
        assertEquals(4, service.precedenceOf(StatusLevel.ISSUE, id("NEEDS_MIGRATION", StatusLevel.ISSUE)));

        // The locked ADR 0015 order reproduced through data alone: TODO < DOING < DONE < INACTIVE.
        assertTrue(service.precedenceOf(StatusLevel.ISSUE, id("DONE", StatusLevel.ISSUE))
            < service.precedenceOf(StatusLevel.ISSUE, id("INACTIVE", StatusLevel.ISSUE)));
    }

    @Test
    void precedenceOfANullIdIsTheMigrationStatusPrecedence() {
        assertEquals(
            service.precedenceOf(StatusLevel.TASK, id("NEEDS_MIGRATION", StatusLevel.TASK)),
            service.precedenceOf(StatusLevel.TASK, null));
    }

    // ── Default status for a fresh row ──────────────────────────────

    @Test
    void theDefaultStatusIsTheLowestPrecedenceAvailableOnePerLevel() {
        assertEquals(id("OPENED", StatusLevel.PROJECT), service.defaultStatusId(StatusLevel.PROJECT));
        assertEquals(id("TODO", StatusLevel.ISSUE), service.defaultStatusId(StatusLevel.ISSUE));
        assertEquals(id("TODO", StatusLevel.TASKLIST), service.defaultStatusId(StatusLevel.TASKLIST));
        assertEquals(id("TODO", StatusLevel.TASK), service.defaultStatusId(StatusLevel.TASK));
    }

    @Test
    void aNewRowNeverDefaultsToTheMigrationStatus() {
        for (StatusLevel level : StatusLevel.values()) {
            StatusBase migration = service.find(level, "NEEDS_MIGRATION").orElseThrow();
            Integer chosen = service.defaultStatusId(level);
            assertNotEquals(migration.id, chosen,
                "a fresh row HAS a status; the fallback is for rows that never got one");
            assertTrue(service.precedenceOf(level, chosen) < service.precedenceOf(level, migration.id),
                "the seeded default outranks the fallback on every level");
        }
    }

    // ── Filter matching keeps unmigrated rows reachable ─────────────

    @Test
    void aFilterNamingTheMigrationStatusMatchesUnmigratedRows() {
        Set<StatusBase> wanted = Set.of(service.find(StatusLevel.ISSUE, "NEEDS_MIGRATION").orElseThrow());

        assertTrue(service.matchesFilter(StatusLevel.ISSUE, null, wanted),
            "?status=NEEDS_MIGRATION must return the rows that actually read as NEEDS_MIGRATION");
    }

    @Test
    void aFilterOnARealStatusExcludesUnmigratedRows() {
        Set<StatusBase> wanted = Set.of(service.find(StatusLevel.ISSUE, "TODO").orElseThrow());

        assertFalse(service.matchesFilter(StatusLevel.ISSUE, null, wanted),
            "an unmigrated row is not TODO, whatever the legacy column said");
        assertTrue(service.matchesFilter(StatusLevel.ISSUE, id("TODO", StatusLevel.ISSUE), wanted));
    }

    @Test
    void anEmptyFilterMatchesEverything() {
        assertTrue(service.matchesFilter(StatusLevel.ISSUE, null, Set.of()));
        assertTrue(service.matchesFilter(StatusLevel.ISSUE, null, null));
    }

    // ── Levels do not bleed into each other ─────────────────────────

    @Test
    @TestTransaction
    void theSameIdMeansDifferentThingsPerLevelAndEachLevelResolvesItsOwn() {
        Task task = Task.create("TL100000", 0, "t", "d", null);
        task.persist();
        // A task with no status id reads as the TASK-level fallback, never the issue one.
        assertEquals("NEEDS_MIGRATION", service.codeOf(StatusLevel.TASK, task));
    }

    private Integer id(String code, StatusLevel level) {
        return service.find(level, code).orElseThrow().id;
    }
}
