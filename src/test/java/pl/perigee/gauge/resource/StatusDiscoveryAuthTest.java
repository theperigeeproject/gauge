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
import static org.hamcrest.CoreMatchers.is;

/**
 * The deliberate asymmetry between discovery and authoring, proven under an auth-enabled profile —
 * the only profile where a 401 or a 403 can discriminate (ADR 0019; a 403 under the default
 * no-auth profile proves nothing, because there is no identity to fail).
 *
 * <ul>
 *   <li>anonymous → <b>401</b>: not a public resource;</li>
 *   <li>a real non-admin API key → <b>200</b>: discovery is not admin-only;</li>
 *   <li>the same key against the admin sibling → <b>403</b>: authoring still is.</li>
 * </ul>
 *
 * <p>That third line is the one that makes the first two meaningful. Without it, "non-admin can
 * read" could just be a forgotten guard that fails open.
 */
@QuarkusTest
@TestProfile(StatusDiscoveryAuthTest.Profile.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class StatusDiscoveryAuthTest {

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

    @Inject UserService userService;
    @Inject ApiKeyService apiKeyService;

    static String nonAdminKey;

    @Test @Order(1)
    void setupNonAdminKey() {
        QuarkusTransaction.run(() -> {
            User user = userService.create("status-discovery-plain", "secret", "user");
            nonAdminKey = apiKeyService.create(user.id, "status-discovery-plain-key", null, null).rawKey;
        });
        Assertions.assertNotNull(nonAdminKey);
    }

    @Test @Order(2)
    void anonymousGets401() {
        given().contentType(ContentType.JSON)
            .when().get("/api/statuses/issue")
            .then().statusCode(401);
    }

    @Test @Order(3)
    void aNonAdminKeyCanReadDiscovery() {
        given()
            .header("Authorization", "Bearer " + nonAdminKey)
            .contentType(ContentType.JSON)
            .when().get("/api/statuses/issue")
            .then().statusCode(200)
            .body("find { it.code == 'TODO' }.active", is(true));
    }

    @Test @Order(4)
    void theSameKeyIsRefusedByTheAdminAuthoringSurface() {
        // Discovery and authoring live at different addresses on purpose; this is what makes
        // "not admin-only" a decision rather than an accidental hole.
        given()
            .header("Authorization", "Bearer " + nonAdminKey)
            .contentType(ContentType.JSON)
            .when().get("/api/admin/statuses/issue")
            .then().statusCode(403)
            .body("error", is("FORBIDDEN"));
    }
}
