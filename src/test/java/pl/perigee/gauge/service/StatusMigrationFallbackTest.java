package pl.perigee.gauge.service;

import pl.perigee.gauge.domain.ConflictException;
import pl.perigee.gauge.domain.StatusBase;
import pl.perigee.gauge.domain.StatusLevel;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The {@code NEEDS_MIGRATION} fallback: a null {@code statusId} resolves to a seeded status
 * instead of throwing. This is what makes the migration optional rather than a boot-time gate —
 * an upgraded database that is never migrated stays correct and visible.
 *
 * <p>Everything asserted here is a property of the <em>read</em> path. Nothing writes the fallback
 * into an entity; the column stays null until the migration actually runs.
 */
@QuarkusTest
class StatusMigrationFallbackTest {

    @Inject
    StatusService service;

    // ── The seed exists, once per level, and carries the MIGRATION attitude ──

    @ParameterizedTest
    @EnumSource(StatusLevel.class)
    void everyLevelHasExactlyOneMigrationStatus(StatusLevel level) {
        long count = service.all(level).stream()
            .filter(s -> StatusService.MIGRATION_ATTITUDE.equals(s.attitudeType))
            .count();
        assertEquals(1, count, level.wire + " must have exactly one MIGRATION status");

        StatusBase fallback = service.migrationStatus(level);
        assertEquals(StatusService.NEEDS_MIGRATION, fallback.code,
            "the seeded fallback is NEEDS_MIGRATION");
        assertEquals("HALTED", fallback.progressLevel);
        assertEquals(StatusService.MIGRATION_ATTITUDE, fallback.attitudeType);
    }

    // ── A null id is legal input, not corruption ─────────────────────

    @ParameterizedTest
    @EnumSource(StatusLevel.class)
    void nullStatusIdResolvesToTheFallbackCode(StatusLevel level) {
        assertEquals(StatusService.NEEDS_MIGRATION, service.codeFor(level, null),
            "a row that was never migrated must render, " + level.wire);
        assertTrue(service.isUnmigrated(null));
        assertFalse(service.isUnmigrated(1));
    }

    @ParameterizedTest
    @EnumSource(StatusLevel.class)
    void nullStatusIdResolvesToTheFallbackRowWithItsAxes(StatusLevel level) {
        StatusBase resolved = service.byId(level, null);
        assertEquals(service.migrationStatus(level).id, resolved.id);
        assertTrue(service.isActive(resolved), "an unmigrated row is attention-worthy, not parked");
        assertFalse(service.isTerminal(resolved), "it must not be hidden by default");
        assertFalse(service.isParked(resolved));
        assertTrue(service.isReadVisible(resolved), "hiding it would look like data loss");
    }

    @Test
    void aRealIdStillWinsAndAnUnknownIdStillThrows() {
        StatusBase doing = service.find(StatusLevel.ISSUE, "DOING").orElseThrow();
        assertEquals("DOING", service.codeFor(StatusLevel.ISSUE, doing.id));

        // Null is handled; a non-null dangling id is still genuine corruption and must stay loud.
        assertThrows(IllegalStateException.class, () -> service.codeFor(StatusLevel.ISSUE, 999_999));
        assertThrows(IllegalStateException.class, () -> service.byId(StatusLevel.ISSUE, 999_999));
    }

    // ── The fallback is not gated by any feature flag ────────────────

    @Test
    void theFallbackIsAvailableUnderEveryFlagCombination() {
        StatusBase fallback = service.migrationStatus(StatusLevel.ISSUE);

        // The flags live on this bean and the test is in the same package, so flipping them is a
        // direct probe rather than a new @TestProfile per combination. Restored at the end because
        // this bean is application-scoped and outlives the method.
        boolean archive = service.archiveEnabled;
        boolean trash = service.trashEnabled;
        boolean extended = service.extendedProgressLevels;
        try {
            for (boolean a : new boolean[]{true, false}) {
                for (boolean t : new boolean[]{true, false}) {
                    for (boolean e : new boolean[]{true, false}) {
                        service.archiveEnabled = a;
                        service.trashEnabled = t;
                        service.extendedProgressLevels = e;
                        assertTrue(service.isAvailable(fallback),
                            "a flag flip must never make the fallback unavailable");
                    }
                }
            }
        } finally {
            service.archiveEnabled = archive;
            service.trashEnabled = trash;
            service.extendedProgressLevels = extended;
        }
    }

    // ── Resolved by attitude, never by code ──────────────────────────

    @Test
    @TestTransaction
    void renamingTheFallbackDoesNotBreakIt() {
        // The whole point: ADR 0020 §2 says renaming a status is free, and §6 forbids behaviour
        // depending on a status name. If migrationStatus() looked up the literal "NEEDS_MIGRATION",
        // this rename would silently break every unmigrated row.
        StatusBase seeded = service.migrationStatus(StatusLevel.ISSUE);
        service.update(StatusLevel.ISSUE, StatusService.NEEDS_MIGRATION,
            new StatusService.StatusSpec("UNMIGRATED", seeded.precedenceValue, seeded.color,
                seeded.progressLevel, seeded.attitudeType));

        assertEquals("UNMIGRATED", service.codeFor(StatusLevel.ISSUE, null),
            "the fallback follows the attitude, so the rename must carry");
        assertEquals(seeded.id, service.migrationStatus(StatusLevel.ISSUE).id);
    }

    // ── The fallback is protected ────────────────────────────────────

    @ParameterizedTest
    @EnumSource(StatusLevel.class)
    void theFallbackCannotBeDeletedUnderAnyPolicy(StatusLevel level) {
        // Refused before the orphan policy is considered, so even the destructive policy cannot
        // remove it. These statuses have no referencing rows today, which is exactly why a naive
        // "unused therefore deletable" check would let it through.
        for (StatusService.RemovalPolicy policy : new StatusService.RemovalPolicy[]{
            null, StatusService.RemovalPolicy.PREVENT,
            StatusService.RemovalPolicy.REMOVE, StatusService.RemovalPolicy.MIGRATE}) {
            ConflictException e = assertThrows(ConflictException.class,
                () -> service.delete(level, StatusService.NEEDS_MIGRATION, policy, "OPENED"),
                level.wire + " fallback must be undeletable (policy=" + policy + ")");
            assertEquals("MIGRATION_STATUS_PROTECTED", e.error());
        }
        assertTrue(service.find(level, StatusService.NEEDS_MIGRATION).isPresent());
    }

    @Test
    @TestTransaction
    void theFallbackCannotBeMovedToAnotherAttitude() {
        ConflictException e = assertThrows(ConflictException.class, () ->
            service.update(StatusLevel.ISSUE, StatusService.NEEDS_MIGRATION,
                new StatusService.StatusSpec(StatusService.NEEDS_MIGRATION, 4, "#fb923c",
                    "HALTED", "REGULAR")));
        assertEquals("MIGRATION_STATUS_PROTECTED", e.error());
    }

    @Test
    @TestTransaction
    void aSecondMigrationStatusIsRejected() {
        ConflictException e = assertThrows(ConflictException.class, () ->
            service.create(StatusLevel.ISSUE,
                new StatusService.StatusSpec("MIGRATION2", 30, "#ffffff", "HALTED",
                    StatusService.MIGRATION_ATTITUDE)));
        assertEquals("DUPLICATE_MIGRATION_STATUS", e.error());
    }

    // ── The existing invariant still means something ─────────────────

    @Test
    @TestTransaction
    void theFallbackDoesNotCountAsAUsableStatusForTheLastStatusInvariant() {
        // Project level seeds OPENED, CLOSED and the fallback. Removing both real statuses must hit
        // the invariant — the fallback is always available, so counting it would make the guard
        // unreachable and leave the level holding nothing but "needs migration".
        service.delete(StatusLevel.PROJECT, "CLOSED", null, null);

        ConflictException e = assertThrows(ConflictException.class,
            () -> service.delete(StatusLevel.PROJECT, "OPENED", null, null));
        assertEquals("LAST_STATUS", e.error());
    }
}
