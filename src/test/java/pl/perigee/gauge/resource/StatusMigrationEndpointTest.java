package pl.perigee.gauge.resource;

import pl.perigee.gauge.domain.Issue;
import pl.perigee.gauge.domain.IssueStatus;
import pl.perigee.gauge.domain.Priority;
import pl.perigee.gauge.domain.User;
import pl.perigee.gauge.service.ApiKeyService;
import pl.perigee.gauge.service.UserService;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;

/**
 * {@code POST /api/admin/migrate-statuses} with authentication ENABLED — the only profile in which
 * the admin guard can discriminate. A 403 under the default (no-auth) profile proves nothing,
 * because no identity exists there at all (ADR 0019).
 *
 * <p>The endpoint fails <b>open</b> if its guard is ever forgotten, so both outcomes are asserted:
 * a real non-admin key must be refused, and the master key must get through to the confirm guard.
 *
 * <p>Unlike {@code migrate-ids}, calling this with {@code confirm=true} is safe and idempotent — it
 * only fills null status ids and never deletes anything — so the destructive-call warning that
 * governs {@code AdminResourceAuthTest} does not apply here.
 */
@QuarkusTest
@TestProfile(StatusMigrationEndpointTest.Profile.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class StatusMigrationEndpointTest {

    public static class Profile implements io.quarkus.test.junit.QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                "gauge.auth.enabled", "true",
                "gauge.auth.seed-on-startup", "true",
                "gauge.auth.admin.username", "admin",
                "gauge.auth.admin.password", "admin",
                "gauge.api-key.access", "test-master-key",
                "gauge.api-key.pepper", "test-pepper"
            );
        }
    }

    private static final String MASTER = "test-master-key";

    @Inject UserService userService;
    @Inject ApiKeyService apiKeyService;
    @Inject EntityManager em;

    static String nonAdminKey;

    // ── Authorization, both outcomes ────────────────────────────────

    @Test @Order(1)
    void setupNonAdminKey() {
        QuarkusTransaction.run(() -> {
            User user = userService.create("status-mig-plain", "secret", "user");
            nonAdminKey = apiKeyService.create(user.id, "status-mig-plain-key", null, null).rawKey;
        });
        Assertions.assertNotNull(nonAdminKey);
    }

    @Test @Order(2)
    void noAuthorizationHeaderReturns401() {
        given().contentType(ContentType.JSON)
            .when().post("/api/admin/migrate-statuses?confirm=true")
            .then().statusCode(401);
    }

    @Test @Order(3)
    void nonAdminKeyIsForbidden() {
        // confirm=true on purpose: proves the role check runs BEFORE the confirm branch.
        given()
            .header("Authorization", "Bearer " + nonAdminKey)
            .contentType(ContentType.JSON)
            .when().post("/api/admin/migrate-statuses?confirm=true")
            .then().statusCode(403)
            .body("error", is("FORBIDDEN"));
    }

    @Test @Order(4)
    void masterKeyWithoutConfirmHitsTheConfirmGuard() {
        given()
            .header("Authorization", "Bearer " + MASTER)
            .contentType(ContentType.JSON)
            .when().post("/api/admin/migrate-statuses?confirm=false")
            .then().statusCode(400)
            .body("error", is("CONFIRM_REQUIRED"));
    }

    // ── The migration itself ────────────────────────────────────────

    @Test @Order(5)
    void seedTwoUnmigratedIssues() {
        QuarkusTransaction.run(() -> {
            em.persist(legacyIssue("EPMIG001", "TODO"));
            em.persist(legacyIssue("EPMIG002", "WORKING"));
        });
    }

    @Test @Order(6)
    void anUnmatchableLegacyCodeIsReportedAndTheRequestStillSucceeds() {
        // 200, not 500 and not a startup abort. An unmatchable value is data, not a request failure.
        given()
            .header("Authorization", "Bearer " + MASTER)
            .contentType(ContentType.JSON)
            .when().post("/api/admin/migrate-statuses?confirm=true")
            .then().statusCode(200)
            .body("migrated.issue", greaterThanOrEqualTo(1))
            .body("unresolved[0].code", is("WORKING"))
            .body("unresolved[0].level", is("issue"))
            .body("unresolved[0].rowCount", is(1))
            .body("complete", is(false));
    }

    @Test @Order(7)
    void theSecondCallMigratesNothing() {
        // Idempotency on real storage: the button is safe to press twice.
        given()
            .header("Authorization", "Bearer " + MASTER)
            .contentType(ContentType.JSON)
            .when().post("/api/admin/migrate-statuses?confirm=true")
            .then().statusCode(200)
            .body("migrated.issue", is(0))
            .body("migrated.project", is(0))
            .body("migrated.tasklist", is(0))
            .body("migrated.task", is(0));
    }

    @Test @Order(8)
    void theMigratedRowGotAStatusIdAndTheLegacyColumnIsUntouched() {
        Issue todo = Issue.findById("EPMIG001");
        Assertions.assertNotNull(todo.statusId, "the resolvable row must have been migrated");
        Assertions.assertEquals("TODO", todo.status,
            "the legacy column is history and is never written by the migration");

        Issue working = Issue.findById("EPMIG002");
        Assertions.assertNull(working.statusId, "the unresolvable row keeps a null id");
        Assertions.assertEquals("WORKING", working.status,
            "the offending value survives so an operator can still see what it was");
    }

    private static Issue legacyIssue(String id, String legacyCode) {
        Issue issue = new Issue(id, "PROJ0001", "t", "d", IssueStatus.TODO, Priority.NORMAL);
        issue.status = legacyCode;
        issue.statusId = null;
        return issue;
    }
}
