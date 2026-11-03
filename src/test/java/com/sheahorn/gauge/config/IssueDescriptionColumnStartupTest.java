package com.sheahorn.gauge.config;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Proves the ALTER in {@link IssueDescriptionColumnStartup} works on a pre-existing
 * {@code VARCHAR(4000)} column that already holds data.
 */
class IssueDescriptionColumnStartupTest {

    @Test
    void widensNarrowColumnWithExistingData() throws Exception {
        try (Connection conn = DriverManager.getConnection("jdbc:h2:mem:alter_probe;DB_CLOSE_DELAY=-1", "sa", "");
             Statement stmt = conn.createStatement()) {

            stmt.execute("CREATE TABLE issues (id VARCHAR(36) PRIMARY KEY, description VARCHAR(4000))");
            stmt.execute("INSERT INTO issues (id, description) VALUES ('a', 'short')");

            stmt.execute(IssueDescriptionColumnStartup.ALTER_SQL);
            // Running it twice must stay harmless (it runs on every boot).
            stmt.execute(IssueDescriptionColumnStartup.ALTER_SQL);

            String longDesc = "y".repeat(50_000);
            try (var ps = conn.prepareStatement("UPDATE issues SET description = ? WHERE id = 'a'")) {
                ps.setString(1, longDesc);
                assertEquals(1, ps.executeUpdate());
            }

            try (ResultSet rs = stmt.executeQuery("SELECT LENGTH(description) FROM issues WHERE id = 'a'")) {
                rs.next();
                assertEquals(50_000, rs.getInt(1));
            }
        }
    }
}
