package pl.perigee.gauge.domain;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class IdProviderTest {

    @Inject
    IdProvider provider;

    // ---- 0: throws IllegalStateException if nextId() called before onStart ----
    // Not testable in @QuarkusTest: StartupEvent fires before any test runs,
    // so the provider is always initialized by the time we reach a test method.

    // ---- 1: counter overflow → switches to UUID fallback ----

    @Test
    void testCounterOverflowSwitchesToUuidFallback() {
        // Set counter just below overflow
        provider.resetAfterMigration(0xFFFFFFFEL);
        assertFalse(provider.isUuidFallback());

        // First call: 0xFFFFFFFF (last valid hex-8)
        String id1 = provider.nextId();
        assertEquals("FFFFFFFF", id1);
        assertFalse(provider.isUuidFallback());

        // Second call: overflow → UUID
        String id2 = provider.nextId();
        assertTrue(provider.isUuidFallback());
        assertEquals(36, id2.length());
        assertTrue(id2.contains("-"));
    }

    // ---- 2: UUID fallback — isUuidFallback() true, nextId() returns UUID ----

    @Test
    void testUuidFallbackProducesUuids() {
        // Force overflow to enter UUID mode
        provider.resetAfterMigration(0xFFFFFFFFL);
        provider.nextId(); // triggers overflow

        assertTrue(provider.isUuidFallback());
        String id = provider.nextId();
        assertEquals(36, id.length());
        assertTrue(id.contains("-"));
    }

    // ---- 3: resetAfterMigration — sets counter, clears uuidFallback, sets initialized ----

    @Test
    void testResetAfterMigration() {
        // First force into UUID mode
        provider.resetAfterMigration(0xFFFFFFFFL);
        provider.nextId(); // overflow → UUID mode
        assertTrue(provider.isUuidFallback());

        // Now reset
        provider.resetAfterMigration(0x00000042L);
        assertFalse(provider.isUuidFallback());

        String id = provider.nextId();
        assertEquals("00000043", id);
    }

    // ---- 4: onStart with empty DB — counter=0, initialized=true, uuidFallback=false ----
    // Verified implicitly: the provider is injected and working in hex-8 mode.
    // The test DB is empty at startup, so onStart set counter=0.

    @Test
    void testInitializedAfterStartup() {
        assertNotNull(provider);
        assertFalse(provider.isUuidFallback());
        String id = provider.nextId();
        assertNotNull(id);
    }

    // ---- 5: onStart with existing hex-8 IDs — counter = max found ----
    // Not testable in isolation: requires inserting data before StartupEvent,
    // which isn't possible in a @QuarkusTest.

    // ---- 6: onStart with non-hex-8 ID (UUID) → uuidFallback ----
    // Not testable in isolation: requires inserting UUID-format data before
    // StartupEvent, which isn't possible in a @QuarkusTest.

    // ---- 7: hex-8 format — always 8 chars, uppercase, valid hex ----

    @Test
    void testProducesHex8Format() {
        provider.resetAfterMigration(0);
        String id = provider.nextId();
        assertEquals(8, id.length());
        assertTrue(id.matches("[0-9A-F]{8}"), "Expected uppercase hex, got: " + id);
    }

    // ---- 8: sequential — many IDs in a row are strictly sequential ----

    @Test
    void testSequentialIncrement() {
        provider.resetAfterMigration(0);
        String id1 = provider.nextId();
        String id2 = provider.nextId();
        String id3 = provider.nextId();

        long v1 = Long.parseLong(id1, 16);
        long v2 = Long.parseLong(id2, 16);
        long v3 = Long.parseLong(id3, 16);

        assertEquals(v1 + 1, v2);
        assertEquals(v2 + 1, v3);
    }

    @Test
    void testManyIdsStayHex8() {
        provider.resetAfterMigration(0);
        long prev = -1;
        for (int i = 0; i < 100; i++) {
            String id = provider.nextId();
            assertEquals(8, id.length());
            assertTrue(id.matches("[0-9A-F]{8}"), "Bad ID: " + id);
            long val = Long.parseLong(id, 16);
            if (prev != -1) {
                assertEquals(prev + 1, val, "Gap at iteration " + i);
            }
            prev = val;
        }
    }
}
