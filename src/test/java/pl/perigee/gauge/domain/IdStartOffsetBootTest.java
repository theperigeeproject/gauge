package pl.perigee.gauge.domain;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Proves {@code gauge.ids.start-offset} is actually wired into the boot scan, not just into a
 * pure helper. The profile boots a fresh (drop-and-create) in-memory DB, so the table scan finds
 * nothing and the very first ID handed out must be the offset itself.
 *
 * Hex form is used deliberately — {@code 0x1000} also exercises the parsing path through
 * the real config source rather than a hardcoded literal.
 */
@QuarkusTest
@TestProfile(IdStartOffsetBootTest.OffsetProfile.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class IdStartOffsetBootTest {

    public static class OffsetProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("gauge.ids.start-offset", "0x1000");
        }
    }

    @Inject
    IdProvider provider;

    @Test
    @Order(1)  // must observe the untouched post-boot state, so it runs first
    void firstIdAfterBootIsTheOffset() {
        assertFalse(provider.isUuidFallback());
        assertEquals("00001000", provider.nextId(),
            "empty DB + start-offset 0x1000 must hand out 00001000 first");
    }

    @Test
    @Order(2)
    void subsequentIdsWalkUpFromTheOffset() {
        // resetAfterMigration applies the same floor, so this is deterministic regardless
        // of the order test methods run in
        provider.resetAfterMigration(0);
        assertEquals("00001000", provider.nextId());
        assertEquals("00001001", provider.nextId());
        assertEquals("00001002", provider.nextId());
    }

    @Test
    @Order(3)
    void offsetIsAFloorNotACapForExistingData() {
        provider.resetAfterMigration(0x2000);
        assertEquals("00002001", provider.nextId(),
            "existing rows above the offset must win over the offset");
    }
}
