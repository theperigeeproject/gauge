package pl.perigee.gauge.config;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;

/**
 * Widens {@code issues.description} on databases created before the configurable limit existed.
 *
 * <p>Hibernate's {@code update} generation will not reliably widen an existing column, and a
 * {@code VARCHAR(4000)} column would turn a legitimate long description into a 500. The ALTER is
 * idempotent and harmless when the column is already wide, or when the table does not exist yet
 * (a fresh database before Hibernate has run its DDL) — hence the swallowed exception.
 *
 * <p>Uses the same startup shape as {@code AuthStartupBean}: {@code @Startup} + {@code @PostConstruct}
 * with {@code QuarkusTransaction.run}, not {@code @Observes StartupEvent} + {@code @Transactional},
 * which is broken on Quarkus 3.x in this codebase (issue 0000055D).
 */
@Startup
@ApplicationScoped
public class IssueDescriptionColumnStartup {

    private static final Logger LOG = Logger.getLogger(IssueDescriptionColumnStartup.class);

    /** Matches {@code IssueDescriptionPolicy.HARD_MAX_LENGTH}. */
    public static final String ALTER_SQL =
        "ALTER TABLE issues ALTER COLUMN description SET DATA TYPE CHARACTER VARYING(1000000)";

    @Inject
    DataSource dataSource;

    @PostConstruct
    void init() {
        QuarkusTransaction.run(() -> {
            try (Connection conn = dataSource.getConnection();
                 Statement stmt = conn.createStatement()) {
                stmt.execute(ALTER_SQL);
                LOG.info("Ensured issues.description is wide enough for long descriptions");
            } catch (Exception e) {
                LOG.debugf("issues.description not altered (%s) - table may not exist yet", e.getMessage());
            }
        });
    }
}
