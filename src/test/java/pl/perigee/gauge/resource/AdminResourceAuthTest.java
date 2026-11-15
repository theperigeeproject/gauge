package pl.perigee.gauge.resource;

import pl.perigee.gauge.domain.User;
import pl.perigee.gauge.service.ApiKeyService;
import pl.perigee.gauge.service.UserService;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.*;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.*;

/**
 * AdminResource with authentication ENABLED — the only profile in which the
 * admin guard can discriminate. AdminResourceTest asserts 403 with no identity
 * at all, which proves nothing about roles.
 *
 * <p>WARNING: never call POST /api/admin/migrate-ids with the master key AND
 * confirm=true. That runs the real IdMigrationService against the test database.
 * The master-key case below deliberately uses confirm=false so the reachable
 * outcome is the 400 CONFIRM_REQUIRED guard — which is exactly what proves
 * authorization cleared without the migration being attempted.
 */
@QuarkusTest
@TestProfile(AdminResourceAuthTest.Profile.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AdminResourceAuthTest {

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

    @Inject
    UserService userService;

    @Inject
    ApiKeyService apiKeyService;

    static String nonAdminKey;

    @Test @Order(1)
    void setupNonAdminKey() {
        QuarkusTransaction.run(() -> {
            User user = userService.create("admin-test-plain", "secret", "user");
            nonAdminKey = apiKeyService.create(user.id, "admin-test-plain-key", null, null).rawKey;
        });
        Assertions.assertNotNull(nonAdminKey, "non-admin API key should have been created");
    }

    @Test @Order(2)
    void noAuthorizationHeaderReturns401() {
        // SecurityFilter aborts before the resource is reached.
        given().contentType(ContentType.JSON)
            .when().post("/api/admin/migrate-ids?confirm=true")
            .then().statusCode(401);
    }

    @Test @Order(3)
    void nonAdminKeyIsForbidden() {
        // confirm=true on purpose: proves the role check runs BEFORE the destructive
        // branch, not merely before the confirm check.
        given()
            .header("Authorization", "Bearer " + nonAdminKey)
            .contentType(ContentType.JSON)
            .when().post("/api/admin/migrate-ids?confirm=true")
            .then().statusCode(403)
            .body("error", is("FORBIDDEN"));
    }

    @Test @Order(4)
    void masterKeyPassesAuthorizationAndHitsConfirmGuard() {
        // THE DISCRIMINATOR. Authorized (so we get past isAdmin) but confirm=false,
        // so nothing destructive happens.
        given()
            .header("Authorization", "Bearer test-master-key")
            .contentType(ContentType.JSON)
            .when().post("/api/admin/migrate-ids?confirm=false")
            .then().statusCode(400)
            .body("error", is("CONFIRM_REQUIRED"));
    }
}
