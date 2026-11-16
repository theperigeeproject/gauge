package pl.perigee.gauge.resource;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Renders the dashboard template through the real Thymeleaf engine.
 *
 * <p>This exists because <b>{@code mvn compile} does not parse Thymeleaf</b> — a broken template
 * passes every build and only fails when a page is actually hit. A render through the engine is the
 * closest thing to that gate that exists without a browser layer in this project, and it covers the
 * one thing that is easy to get wrong here: {@code th:if="${isAdmin}"} around the maintenance card.
 */
@QuarkusTest
class DashboardMaintenanceCardTest {

    @Inject TemplateEngine templateEngine;

    @Test
    void theMaintenanceCardRendersForAnAdmin() {
        Context ctx = new Context();
        ctx.setVariable("isAdmin", true);

        String html = templateEngine.process("dashboard", ctx);

        assertTrue(html.contains("id=\"btn-migrate-statuses\""),
            "the admin must get a migrate control on the dashboard");
        assertTrue(html.contains("Migrate statuses"));
        assertTrue(html.contains("/api/admin/migrate-statuses"),
            "the button's target must match the endpoint that actually exists");
    }

    @Test
    void theMaintenanceCardIsAbsentForANonAdmin() {
        Context ctx = new Context();
        ctx.setVariable("isAdmin", false);

        String html = templateEngine.process("dashboard", ctx);

        assertFalse(html.contains("id=\"btn-migrate-statuses\""),
            "hiding is cosmetic — the endpoint guards itself — but a non-admin must not be invited");
    }
}
