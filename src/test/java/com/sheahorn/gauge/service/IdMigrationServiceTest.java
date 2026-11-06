package pl.perigee.gauge.service;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class IdMigrationServiceTest {

    @Inject
    IdMigrationService migrationService;

    @Test
    void testGuardThrowsWithoutJvmFlag() {
        // The migration is guarded by a JVM property check.
        // Without -Dangelitrack.migration.i-know-what-i-am-doing=true,
        // it must throw IllegalStateException.
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> {
            migrationService.migrate();
        });
        assertTrue(ex.getMessage().contains("one-time destructive operation"));
    }

    // The remaining tests from the tasklist (empty DB, UUID→hex8 migration,
    // already-migrated DB, parentId FK update) are intentionally NOT implemented.
    // They require the JVM flag to be set, which would actually run the migration
    // against the test database. The tasklist itself marks these as
    // "commented out, reference only" — the migration has already been performed
    // in production and the code is preserved for reference.
}
