package pl.perigee.gauge.service;

import pl.perigee.gauge.domain.ConflictException;
import pl.perigee.gauge.domain.Issue;
import pl.perigee.gauge.domain.Priority;
import pl.perigee.gauge.domain.StatusBase;
import pl.perigee.gauge.domain.StatusLevel;
import pl.perigee.gauge.domain.ValidationException;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Increment 2 of 0000186F: the status service — caches, lookup maps, code&lt;-&gt;id translation,
 * the two axes as semantics, and removal policies.
 *
 * <p>Default profile. Auth is irrelevant here and the schema is built by {@code drop-and-create},
 * so the seeder has already produced the 30 rows.
 */
@QuarkusTest
class StatusServiceTest {

    @Inject
    StatusService service;

    @Inject
    StatusReferenceScanner scanner;

    // ── Caches ──────────────────────────────────────────────────────

    @Test
    void cachesHoldTheSeededStatusesPerLevel() {
        assertEquals(3, service.all(StatusLevel.PROJECT).size());
        assertEquals(5, service.all(StatusLevel.ISSUE).size());
        assertEquals(5, service.all(StatusLevel.TASKLIST).size());
        assertEquals(6, service.all(StatusLevel.TASK).size());
    }

    @Test
    void cachesAreOrderedByPrecedence() {
        List<StatusBase> issues = service.all(StatusLevel.ISSUE);
        assertEquals(List.of("TODO", "DOING", "DONE", "INACTIVE", "NEEDS_MIGRATION"),
            issues.stream().map(s -> s.code).toList());
    }

    @Test
    void lookupMapsExposeTheFixedOrdinals() {
        assertEquals(6, service.progressLevels().size());
        assertEquals(0, service.progressLevels().get("NASCENT"));
        assertEquals(5, service.progressLevels().get("FINALIZED"));
        assertEquals(5, service.attitudeTypes().size());
        assertEquals(1, service.attitudeTypes().get("EXCLUDED"));
        assertEquals(3, service.attitudeTypes().get("TRASH"));
        assertEquals(4, service.attitudeTypes().get("MIGRATION"));
    }

    @Test
    @TestTransaction
    void cacheIsRebuiltAfterCreate() {
        int before = service.all(StatusLevel.ISSUE).size();
        StatusBase created = service.create(StatusLevel.ISSUE,
            new StatusService.StatusSpec("REVIEWING", 9, "#123456", "ONGOING", "REGULAR"));

        assertEquals(before + 1, service.all(StatusLevel.ISSUE).size(),
            "the cache must be rebuilt, not merely left stale");
        assertTrue(service.available(StatusLevel.ISSUE).stream()
            .anyMatch(s -> s.code.equals("REVIEWING")));

        service.delete(StatusLevel.ISSUE, "REVIEWING", null, null);
        assertEquals(before, service.all(StatusLevel.ISSUE).size(),
            "the removal must also rebuild");
        assertNotNull(created);
    }

    // ── Availability and flags ──────────────────────────────────────

    @Test
    void excludedIsStillAvailableBecauseItsFlagDoesNotExist() {
        // EXCLUDED is a normal, always-usable attitude — it is the one reproducing INACTIVE.
        assertTrue(service.available(StatusLevel.ISSUE).stream()
            .anyMatch(s -> s.code.equals("INACTIVE")));
    }

    @Test
    @TestTransaction
    void archiveIsAuthorableBecauseEnableArchiveDefaultsTrue() {
        StatusBase archived = service.create(StatusLevel.ISSUE,
            new StatusService.StatusSpec("ARCHIVED_ISSUE", 8, "#000000", "FINISHED", "ARCHIVE"));
        assertTrue(service.available(StatusLevel.ISSUE).stream()
            .anyMatch(s -> s.code.equals("ARCHIVED_ISSUE")),
            "enable.archive defaults true, so an ARCHIVE status must be usable");
        service.delete(StatusLevel.ISSUE, "ARCHIVED_ISSUE", null, null);
        assertNotNull(archived);
    }

    @Test
    @TestTransaction
    void trashIsRejectedBecauseEnableTrashDefaultsFalse() {
        ValidationException e = assertThrows(ValidationException.class, () ->
            service.create(StatusLevel.ISSUE,
                new StatusService.StatusSpec("TRASHED_ISSUE", 7, "#000000", "FINISHED", "TRASH")));
        assertEquals("ATTITUDE_DISABLED", e.error());
        assertTrue(e.getMessage().contains("gauge.status.trash-enabled"),
            "the message must name the property that would allow it");
    }

    @Test
    @TestTransaction
    void extendedProgressLevelsAreRejectedBecauseEnableExtendedDefaultsFalse() {
        ValidationException e = assertThrows(ValidationException.class, () ->
            service.create(StatusLevel.ISSUE,
                new StatusService.StatusSpec("NASCENT_ISSUE", 7, "#000", "NASCENT", "REGULAR")));
        assertEquals("PROGRESS_LEVEL_DISABLED", e.error());
    }

    // ── Semantics through the axes, never a code ────────────────────

    @Test
    void terminalParkedAndActiveAreDerivedFromTheTwoAxes() {
        StatusBase done = service.find(StatusLevel.ISSUE, "DONE").orElseThrow();
        StatusBase inactive = service.find(StatusLevel.ISSUE, "INACTIVE").orElseThrow();
        StatusBase doing = service.find(StatusLevel.ISSUE, "DOING").orElseThrow();

        // The four locked INACTIVE rules from ADR 15, expressed without naming a status.
        assertTrue(service.isTerminal(done));
        assertFalse(service.isActive(done));

        assertTrue(service.isParked(inactive));
        assertFalse(service.isTerminal(inactive), "parked is not finished");
        assertFalse(service.isActive(inactive));

        assertTrue(service.isActive(doing));
        assertFalse(service.isTerminal(doing));
        assertFalse(service.isParked(doing));
    }

    @Test
    void cancelledCountsAsTerminalToo() {
        StatusBase cancelled = service.find(StatusLevel.TASKLIST, "CANCELED").orElseThrow();
        assertTrue(service.isTerminal(cancelled),
            "FINALIZED is terminal, so CANCELED inherits the hide-by-default rule");
    }

    @Test
    void failedIsAHaltedRegularStatusSoItStillBubbles() {
        StatusBase failed = service.find(StatusLevel.TASK, "FAILED").orElseThrow();
        assertTrue(service.isActive(failed),
            "FAILED is HALTED but REGULAR — a blocker, not a parked status");
    }

    @Test
    void archiveAndTrashAreNotReadVisible() {
        StatusBase doing = service.find(StatusLevel.ISSUE, "DOING").orElseThrow();
        assertTrue(service.isReadVisible(doing));
        assertTrue(service.isReadVisible(service.find(StatusLevel.ISSUE, "INACTIVE").orElseThrow()),
            "a parked status is still shown — it is not archived");
    }

    // ── Code ↔ id ───────────────────────────────────────────────────

    @Test
    void resolveForWriteAcceptsAnyCaseAndReturnsTheInternalId() {
        StatusBase doing = service.find(StatusLevel.ISSUE, "DOING").orElseThrow();
        assertEquals(doing.id, service.resolveForWrite(StatusLevel.ISSUE, "doing"));
        assertEquals(doing.id, service.resolveForWrite(StatusLevel.ISSUE, "  DoInG  "));
    }

    @Test
    void codeForReturnsTheCodeAndNeverTheId() {
        StatusBase doing = service.find(StatusLevel.ISSUE, "DOING").orElseThrow();
        String code = service.codeFor(StatusLevel.ISSUE, doing.id);
        assertEquals("DOING", code);
        assertNotEquals(String.valueOf(doing.id), code,
            "the surface must never see an internal id");
    }

    @Test
    void unknownCodeIsRejectedNamingTheValidSet() {
        ValidationException e = assertThrows(ValidationException.class,
            () -> service.resolveForWrite(StatusLevel.ISSUE, "MEDIUM"));
        assertEquals("INVALID_STATUS", e.error());
        assertTrue(e.getMessage().contains("TODO"), "the message must enumerate valid codes");
        assertTrue(e.getMessage().contains("INACTIVE"));
    }

    @Test
    void nullOrBlankStatusIsRejectedNotDefaulted() {
        assertEquals("INVALID_STATUS",
            assertThrows(ValidationException.class,
                () -> service.resolveForWrite(StatusLevel.ISSUE, null)).error());
        assertEquals("INVALID_STATUS",
            assertThrows(ValidationException.class,
                () -> service.resolveForWrite(StatusLevel.ISSUE, "  ")).error());
    }

    @Test
    void aStatusFromAnotherLevelIsNotResolvable() {
        // DONE exists for issues; asking for it on the project level must not silently succeed.
        assertThrows(ValidationException.class,
            () -> service.resolveForWrite(StatusLevel.PROJECT, "DONE"));
    }

    @Test
    void invalidLevelSegmentIsRejectedNamingTheValidSet() {
        ValidationException e = assertThrows(ValidationException.class,
            () -> StatusLevel.fromWire("milestone"));
        assertEquals("INVALID_STATUS_LEVEL", e.error());
        assertTrue(e.getMessage().contains("tasklist"));
    }

    // ── Duplicates ──────────────────────────────────────────────────

    @Test
    @TestTransaction
    void duplicateCodeIsAConflictNotASilentMerge() {
        ConflictException e = assertThrows(ConflictException.class, () ->
            service.create(StatusLevel.ISSUE,
                new StatusService.StatusSpec("DOING", 42, "#fff", "ONGOING", "REGULAR")));
        assertEquals("DUPLICATE_STATUS_CODE", e.error());
    }

    @Test
    @TestTransaction
    void duplicatePrecedenceIsAConflict() {
        ConflictException e = assertThrows(ConflictException.class, () ->
            service.create(StatusLevel.ISSUE,
                new StatusService.StatusSpec("NEW_ONE", 1, "#fff", "ONGOING", "REGULAR")));
        assertEquals("DUPLICATE_PRECEDENCE", e.error());
    }

    // ── Removal policies ────────────────────────────────────────────

    @Test
    @TestTransaction
    void unusedStatusDeletesWithoutAPolicy() {
        service.create(StatusLevel.TASK,
            new StatusService.StatusSpec("SPARE", 20, "#fff", "ONGOING", "REGULAR"));
        service.delete(StatusLevel.TASK, "SPARE", null, null);
        assertTrue(service.find(StatusLevel.TASK, "SPARE").isEmpty());
    }

    @Test
    @TestTransaction
    void noPolicyButReferencedRowsIsAConflict() {
        Issue issue = Issue.create("PROJ0000", "t", "d", Priority.NORMAL);
        issue.persist();

        // The issue carries the legacy code 'TODO', which is what entities reference today.
        long refs = scanner.count(StatusLevel.ISSUE, service.find(StatusLevel.ISSUE, "TODO").orElseThrow());
        assertTrue(refs >= 1, "the persisted issue must be counted as a reference");

        ConflictException e = assertThrows(ConflictException.class,
            () -> service.delete(StatusLevel.ISSUE, "TODO", null, null));
        assertEquals("STATUS_IN_USE", e.error());
        assertTrue(e.getMessage().contains("policy="), "the message must tell the caller what to send");
    }

    @Test
    @TestTransaction
    void preventPolicyRefusesEvenWithManyReferences() {
        Issue issue = Issue.create("PROJ0003", "t", "d", Priority.NORMAL);
        issue.persist();

        ConflictException e = assertThrows(ConflictException.class,
            () -> service.delete(StatusLevel.ISSUE, "TODO", StatusService.RemovalPolicy.PREVENT, null));
        assertEquals("STATUS_IN_USE", e.error());
        assertTrue(service.find(StatusLevel.ISSUE, "TODO").isPresent(),
            "prevent must leave both the status and the rows alone");
    }

    @Test
    @TestTransaction
    void migratePolicyRewritesRowsThenDeletesTheStatus() {
        Issue issue = Issue.create("PROJ0001", "t", "d", Priority.NORMAL);
        issue.persist();
        String issueId = issue.id;

        service.delete(StatusLevel.ISSUE, "TODO", StatusService.RemovalPolicy.MIGRATE, "DOING");

        assertTrue(service.find(StatusLevel.ISSUE, "TODO").isEmpty(), "the status is gone");
        // Must look the issue up by its OWN id — Issue.create() takes a project id.
        Issue reloaded = Issue.findById(issueId);
        assertNotNull(reloaded, "the row must survive a migration");
        assertEquals("DOING", reloaded.status, "the referencing row was migrated");
    }

    @Test
    @TestTransaction
    void removePolicyCascadeDeletesRows() {
        Issue issue = Issue.create("PROJ0002", "t", "d", Priority.NORMAL);
        issue.persist();
        String issueId = issue.id;

        service.delete(StatusLevel.ISSUE, "TODO", StatusService.RemovalPolicy.REMOVE, null);

        assertTrue(service.find(StatusLevel.ISSUE, "TODO").isEmpty());
        assertNull(Issue.findById(issueId), "the referencing row was cascade-deleted");
    }

    @Test
    @TestTransaction
    void migrateWithoutATargetIsRejected() {
        Issue issue = Issue.create("PROJ0004", "t", "d", Priority.NORMAL);
        issue.persist();

        ValidationException e = assertThrows(ValidationException.class,
            () -> service.delete(StatusLevel.ISSUE, "TODO", StatusService.RemovalPolicy.MIGRATE, null));
        assertEquals("MIGRATE_TARGET_REQUIRED", e.error());
    }

    @Test
    @TestTransaction
    void migrateTargetMustExistOnTheSameLevel() {
        Issue issue = Issue.create("PROJ0005", "t", "d", Priority.NORMAL);
        issue.persist();

        assertThrows(ValidationException.class, () ->
            service.delete(StatusLevel.ISSUE, "TODO", StatusService.RemovalPolicy.MIGRATE, "NOPE"));
    }

    @Test
    @TestTransaction
    void migrateTargetCannotBeTheStatusBeingRemoved() {
        Issue issue = Issue.create("PROJ0006", "t", "d", Priority.NORMAL);
        issue.persist();

        ValidationException e = assertThrows(ValidationException.class, () ->
            service.delete(StatusLevel.ISSUE, "TODO", StatusService.RemovalPolicy.MIGRATE, "TODO"));
        assertEquals("MIGRATE_TARGET_SELF", e.error());
    }

    @Test
    void invalidPolicyNameIsRejectedNamingTheValidSet() {
        ValidationException e = assertThrows(ValidationException.class,
            () -> StatusService.RemovalPolicy.fromWire("ignore"));
        assertEquals("INVALID_REMOVAL_POLICY", e.error());
        assertTrue(e.getMessage().contains("migrate"));
    }

    @Test
    @TestTransaction
    void removingAnUnknownStatusIsRejected() {
        ValidationException e = assertThrows(ValidationException.class,
            () -> service.delete(StatusLevel.ISSUE, "GHOST", null, null));
        assertEquals("UNKNOWN_STATUS", e.error());
    }

    // ── Invariant ───────────────────────────────────────────────────

    @Test
    @TestTransaction
    void theLastAvailableStatusCannotBeRemoved() {
        // Project level starts with exactly OPENED and CLOSED. Remove one so removing the other
        // would empty the level.
        service.delete(StatusLevel.PROJECT, "CLOSED", null, null);

        ConflictException e = assertThrows(ConflictException.class,
            () -> service.delete(StatusLevel.PROJECT, "OPENED", null, null));
        assertEquals("LAST_STATUS", e.error());
        // @TestTransaction rolls this whole method back, so OPENED and CLOSED survive for the
        // tests that run after it. No manual restore, and nothing to get out of sync.
    }
}
