package pl.perigee.gauge.repository;

import pl.perigee.gauge.domain.ProgressLevelDef;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Database access for the fixed progress levels. Seeded at startup, never written by the surface.
 *
 * <p>The reference from a status row is by {@code name}, so {@link #findByName(String)} is the
 * lookup that matters; {@code findById} exists only because every table has a key.
 */
@ApplicationScoped
public class ProgressLevelRepository {

    @Transactional
    public ProgressLevelDef save(ProgressLevelDef level) {
        level.persist();
        return level;
    }

    public Optional<ProgressLevelDef> findById(Integer id) {
        return ProgressLevelDef.findByIdOptional(id);
    }

    public Optional<ProgressLevelDef> findByName(String name) {
        return ProgressLevelDef.find("name", name).firstResultOptional();
    }

    /** Ordered by {@code ordinal} ascending — the fixed workflow order. */
    public List<ProgressLevelDef> listAll() {
        return ProgressLevelDef.list("ORDER BY ordinal ASC");
    }

    public long count() {
        return ProgressLevelDef.count();
    }
}
