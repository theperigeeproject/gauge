package pl.perigee.gauge.domain;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

@ApplicationScoped
public class IdProvider {

    private static final Logger LOG = Logger.getLogger(IdProvider.class);
    private static final long MAX_HEX = 0xFFFFFFFFL;

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
     * Called after migration: reset to hex-8 mode with counter at the given max.
     */
    public void resetAfterMigration(long maxId) {
        uuidFallback = false;
        counter.set(maxId);
        initialized = true;
        LOG.infof("IdProvider reset after migration. Max ID = %08X, next will be %08X", maxId, maxId + 1);
    }

    @Transactional
    void onStart(@Observes StartupEvent ev) {
        LOG.info("Initializing IdProvider – scanning existing IDs…");
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

        counter.set(maxFound);
        initialized = true;
        LOG.infof("IdProvider ready. Last seen ID = %08X, next will be %08X", maxFound, maxFound + 1);
    }

    private static boolean isHex8(String s) {
        return s != null && s.length() == 8 && s.matches("[0-9A-Fa-f]{8}");
    }
}
