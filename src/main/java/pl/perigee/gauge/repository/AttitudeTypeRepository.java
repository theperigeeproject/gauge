package pl.perigee.gauge.repository;

import pl.perigee.gauge.domain.AttitudeTypeDef;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Database access for the fixed attitudes. Seeded at startup, never written by the surface.
 *
 * <p>Status rows reference these by {@code name}; {@code findById} exists only because every table
 * has a key.
 */
@ApplicationScoped
public class AttitudeTypeRepository {

    @Transactional
    public AttitudeTypeDef save(AttitudeTypeDef attitude) {
        attitude.persist();
        return attitude;
    }

    public Optional<AttitudeTypeDef> findById(Integer id) {
        return AttitudeTypeDef.findByIdOptional(id);
    }

    public Optional<AttitudeTypeDef> findByName(String name) {
        return AttitudeTypeDef.find("name", name).firstResultOptional();
    }

    /** Ordered by {@code ordinal} ascending. */
    public List<AttitudeTypeDef> listAll() {
        return AttitudeTypeDef.list("ORDER BY ordinal ASC");
    }

    public long count() {
        return AttitudeTypeDef.count();
    }
}
