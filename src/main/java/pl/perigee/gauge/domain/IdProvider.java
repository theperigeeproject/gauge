package pl.perigee.gauge.domain;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

@ApplicationScoped
public class IdProvider {

    private static final Logger LOG = Logger.getLogger(IdProvider.class);
    private static final long MAX_HEX = 0xFFFFFFFFL;

    /**
     * Lowest ID the sequence may hand out. Acts as a FLOOR, never a cap — existing
     * rows always win, because reusing an ID that is already in the database would
     * be a primary-key collision. Decimal or 0x-prefixed hex; kept as String so
     * a hex literal survives config parsing.
     */
    @ConfigProperty(name = "gauge.ids.start-offset", defaultValue = "0")
    String startOffsetRaw;

    private final AtomicLong counter = new AtomicLong(0);
    private volatile boolean initialized = false;
    private volatile boolean uuidFallback = false;

    @Inject
    EntityManager em;

    /**
     * Returns the next ID. Throws if the provider hasn't been initialized yet.
     */
    public String nextId() {
        if (!initialized) {
            throw new IllegalStateException("IdProvider not yet initialized");
        }
        if (uuidFallback) {
            return UUID.randomUUID().toString();
        }
        long val = counter.incrementAndGet();
        if (val > MAX_HEX) {
            LOG.warn("IdProvider counter overflow, switching to UUID fallback");
            uuidFallback = true;
            return UUID.randomUUID().toString();
        }
        return String.format("%08X", val);
    }

    public boolean isUuidFallback() {
        return uuidFallback;
    }

    /**
     * Called after migration: reset to hex-8 mode with counter at the given max,
     * still floored by the configured start offset.
     */
    public void resetAfterMigration(long maxId) {
        long offset = parseOffset(startOffsetRaw);
        long start = resolveStart(maxId, offset);
        uuidFallback = false;
        counter.set(start);
        initialized = true;
        LOG.infof("IdProvider reset after migration. Max ID = %08X, start-offset = %08X, next will be %08X",
            maxId, offset, start + 1);
    }

    @Transactional
    void onStart(@Observes StartupEvent ev) {
        long offset = parseOffset(startOffsetRaw);
        LOG.infof("Initializing IdProvider – scanning existing IDs… (start-offset = %08X)", offset);
        counter.set(0);

        // Tables managed by IdProvider (User and ApiKey stay on UUID)
        String[] tables = {"projects", "issues", "tasklists", "tasks"};
        long maxFound = 0;

        for (String table : tables) {
            try {
                @SuppressWarnings("unchecked")
                List<String> results = em.createNativeQuery(
                    "SELECT id FROM " + table + " ORDER BY id DESC LIMIT 1"
                ).getResultList();

                if (results.isEmpty()) continue;

                String id = results.get(0);
                if (!isHex8(id)) {
                    LOG.infof("Found non-hex-8 ID '%s' in table '%s', switching to UUID mode.", id, table);
                    uuidFallback = true;
                    initialized = true;
                    return;
                }

                long val = Long.parseLong(id, 16);
                if (val > maxFound) maxFound = val;

            } catch (Exception e) {
                LOG.warnf(e, "Error scanning table '%s'", table);
            }
        }

        long start = resolveStart(maxFound, offset);
        counter.set(start);
        initialized = true;
        LOG.infof("IdProvider ready. Highest ID in DB = %08X, start-offset = %08X, next will be %08X",
            maxFound, offset, start + 1);
    }

    /**
     * The offset is a floor: the counter may never sit below {@code offset - 1}, and
     * never below the highest ID already in the database. Returns the counter value,
     * i.e. the ID handed out next is {@code resolveStart(...) + 1}.
     */
    static long resolveStart(long dbMax, long offset) {
        long floor = Math.max(0L, offset) - 1L;   // offset 0 ⇒ floor -1 ⇒ irrelevant
        return Math.max(dbMax, floor);
    }

    /**
     * Parses {@code gauge.ids.start-offset}. Blank ⇒ 0. Negative ⇒ 0. Above 0xFFFFFFFF ⇒
     * fail fast, because every ID would be in UUID-fallback territory from the first call.
     */
    static long parseOffset(String raw) {
        if (raw == null || raw.isBlank()) return 0L;
        String s = raw.trim();
        long value;
        try {
            if (s.startsWith("0x") || s.startsWith("0X")) {
                value = Long.parseLong(s.substring(2), 16);
            } else {
                value = Long.parseLong(s);
            }
        } catch (NumberFormatException e) {
            throw new IllegalStateException(String.format(
                "Invalid gauge.ids.start-offset '%s': expected decimal or 0x-prefixed hex", raw), e);
        }
        if (value < 0) {
            LOG.warnf("gauge.ids.start-offset %d is negative, treating as 0", value);
            return 0L;
        }
        if (value > MAX_HEX) {
            throw new IllegalStateException(String.format(
                "gauge.ids.start-offset %d exceeds the hex-8 range (max %d / 0xFFFFFFFF)", value, MAX_HEX));
        }
        return value;
    }

    private static boolean isHex8(String s) {
        return s != null && s.length() == 8 && s.matches("[0-9A-Fa-f]{8}");
    }
}
