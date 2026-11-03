package com.sheahorn.gauge.config;

import com.sheahorn.gauge.domain.StatusDefaults;
import com.sheahorn.gauge.domain.StatusDefinition;
import com.sheahorn.gauge.domain.StatusLevel;
import com.sheahorn.gauge.repository.StatusDefinitionRepository;
import com.sheahorn.gauge.service.StatusRegistry;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

/**
 * Seeds {@code status_definitions} from {@link StatusDefaults} when the table is empty.
 *
 * <p>The table is the source of truth at runtime; the defaults exist so a fresh database
 * (or one whose Hibernate DDL is {@code none}) still answers correctly. Seeding is
 * idempotent: rows are only written when a level has no rows at all.
 */
@Startup
@ApplicationScoped
public class StatusSeedStartup {

    private static final Logger LOG = Logger.getLogger(StatusSeedStartup.class);

    @Inject
    StatusDefinitionRepository repository;

    @Inject
    StatusRegistry registry;

    @PostConstruct
    void init() {
        try {
            QuarkusTransaction.run(() -> {
                int seeded = 0;
                for (StatusLevel level : StatusLevel.values()) {
                    if (!repository.findByLevel(level.name()).isEmpty()) {
                        continue;
                    }
                    for (StatusDefinition template : StatusDefaults.of(level)) {
                        repository.save(StatusDefinition.create(
                            template.code,
                            template.label,
                            template.level(),
                            template.category(),
                            template.rank,
                            template.color,
                            template.showByDefault,
                            true
                        ));
                        seeded++;
                    }
                }
                if (seeded > 0) {
                    LOG.infof("Seeded %d status definitions", seeded);
                }
            });
        } catch (Exception e) {
            // A missing table is survivable: StatusRegistry falls back to StatusDefaults.
            // Survivable: StatusRegistry falls back to StatusDefaults, and the next boot
            // (with the id provider initialised) seeds for real.
            LOG.warnf("Could not seed status definitions: %s", e.getMessage());
        } finally {
            registry.invalidate();
        }
    }
}
