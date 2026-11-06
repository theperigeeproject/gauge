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

@Startup
@ApplicationScoped
public class FavoritesTableStartup {

    private static final Logger LOG = Logger.getLogger(FavoritesTableStartup.class);

    @Inject
    DataSource dataSource;

    @PostConstruct
    void init() {
        QuarkusTransaction.run(() -> {
            try (Connection conn = dataSource.getConnection();
                 Statement stmt = conn.createStatement()) {
                stmt.execute(
                    "CREATE TABLE IF NOT EXISTS user_favorites (" +
                    "user_id VARCHAR(128) NOT NULL, " +
                    "project_id VARCHAR(36) NOT NULL, " +
                    "PRIMARY KEY (user_id, project_id)" +
                    ")"
                );
                LOG.info("Ensured user_favorites table exists");
            } catch (Exception e) {
                LOG.warnf("Could not create user_favorites table: %s", e.getMessage());
            }
        });
    }
}
