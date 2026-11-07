package pl.perigee.gauge.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure unit tests for the {@code gauge.ids.start-offset} rule. No Quarkus, no DB —
 * see {@link IdStartOffsetBootTest} for the end-to-end wiring proof.
 */
class IdStartOffsetTest {

    private static final long MAX_HEX = 0xFFFFFFFFL;

    // ── parseOffset ────────────────────────────────────────────────────

    @Test
    void parseDecimal() {
        assertEquals(4096L, IdProvider.parseOffset("4096"));
    }

    @Test
    void parseHex() {
        assertEquals(0x1000L, IdProvider.parseOffset("0x1000"));
        assertEquals(0x1000L, IdProvider.parseOffset("0X1000"));
    }

    @Test
    void parseTrimsWhitespace() {
        assertEquals(1234L, IdProvider.parseOffset("  1234  "));
    }

    @Test
    void parseBlankIsZero() {
        assertEquals(0L, IdProvider.parseOffset(null));
        assertEquals(0L, IdProvider.parseOffset(""));
        assertEquals(0L, IdProvider.parseOffset("   "));
    }

    @Test
    void parseNegativeIsZero() {
        assertEquals(0L, IdProvider.parseOffset("-5"));
    }

    @Test
    void parseMaxHexIsAllowed() {
        assertEquals(MAX_HEX, IdProvider.parseOffset("4294967295"));
        assertEquals(MAX_HEX, IdProvider.parseOffset("0xFFFFFFFF"));
        assertEquals(MAX_HEX, IdProvider.parseOffset("0xffffffff"));
    }

    @Test
    void bareValueIsAlwaysDecimal() {
        // no implicit hex — "100" means one hundred, not 0x100
        assertEquals(100L, IdProvider.parseOffset("100"));
        assertEquals(0x100L, IdProvider.parseOffset("0x100"));
    }

    @Test
    void parseAboveHex8RangeFails() {
        assertThrows(IllegalStateException.class, () -> IdProvider.parseOffset("4294967296"));
        assertThrows(IllegalStateException.class, () -> IdProvider.parseOffset("0x100000000"));
    }

    @Test
    void parseGarbageFails() {
        assertThrows(IllegalStateException.class, () -> IdProvider.parseOffset("abc"));
        assertThrows(IllegalStateException.class, () -> IdProvider.parseOffset("0x"));
        assertThrows(IllegalStateException.class, () -> IdProvider.parseOffset("12x"));
    }

    // ── resolveStart: the floor rule ────────────────────────────────────

    @Test
    void emptyDbWithOffsetStartsAtOffset() {
        assertEquals(4095L, IdProvider.resolveStart(0, 4096));
    }

    @Test
    void dbBelowOffsetIsLiftedToOffset() {
        assertEquals(4095L, IdProvider.resolveStart(10, 4096));
    }

    @Test
    void dbExactlyOneBelowOffsetStartsAtOffset() {
        // dbMax = 4095 means 0x1000 is still free ⇒ first ID is exactly the offset
        assertEquals(4095L, IdProvider.resolveStart(4095, 4096));
    }

    @Test
    void dbAtOffsetContinuesAboveIt() {
        assertEquals(4096L, IdProvider.resolveStart(4096, 4096));
    }

    @Test
    void dbAboveOffsetWins() {
        assertEquals(9999L, IdProvider.resolveStart(9999, 4096));
    }

    @Test
    void offsetZeroIsTodaysBehaviour() {
        assertEquals(0L, IdProvider.resolveStart(0, 0));
        assertEquals(1234L, IdProvider.resolveStart(1234, 0));
    }

    @Test
    void negativeOffsetBehavesLikeZero() {
        assertEquals(0L, IdProvider.resolveStart(0, -7));
        assertEquals(1234L, IdProvider.resolveStart(1234, -7));
    }

    @Test
    void offsetAtMaxHexLeavesOneIdableSlot() {
        assertEquals(MAX_HEX - 1, IdProvider.resolveStart(0, MAX_HEX));
    }

    @Test
    void dbAtMaxHexIsPreserved() {
        assertEquals(MAX_HEX, IdProvider.resolveStart(MAX_HEX, 4096));
    }

    @Test
    void resolveStartIsIdempotent() {
        // restarting with the same offset must not move the counter again
        long start = IdProvider.resolveStart(4095, 4096);
        assertEquals(start, IdProvider.resolveStart(start, 4096));
    }

    @Test
    void nextIdAfterResolveNeverCollidesWithDbMax() {
        for (long dbMax : new long[]{0, 1, 4095, 4096, 4097, 100000}) {
            for (long offset : new long[]{0, 1, 4096, 100000}) {
                long nextId = IdProvider.resolveStart(dbMax, offset) + 1;
                assertTrue(nextId > dbMax,
                    "nextId " + nextId + " would collide with dbMax " + dbMax + " (offset " + offset + ")");
                assertTrue(nextId >= offset,
                    "nextId " + nextId + " below offset " + offset + " (dbMax " + dbMax + ")");
            }
        }
    }
}
