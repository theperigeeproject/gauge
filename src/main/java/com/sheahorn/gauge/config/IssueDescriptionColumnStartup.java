package com.sheahorn.gauge.config;

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
 * <p>Hibernate's {@code update} mode will not reliably widen an existing column, and a
 * {@code VARCHAR(4000)} column would turn a legitimate long description into a 500. The ALTER is
 * idempotent and harmless when the column is already wide (or absent).
 */
@Startup
@ApplicationScoped
public class IssueDescriptionColumnStartup {

    private static final Logger LOG = Logger.getLogger(IssueDescriptionColumnStartup.class);

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
                LOG.debugf("issues.description not altered (%s) — table may not exist yet", e.getMessage());
            }
        });
    }
}
