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
import static org.hamcrest.Matchers.*;

/**
 * /api/users with authentication ENABLED. This is the class that actually
 * exercises the admin guard: UserResourceTest only pins what happens when no
 * identity exists at all.
 *
 * Ordered because state builds up: a user is created, then deleted.
 * The seeded admin is never deleted — other cases in this class need it.
 */
@QuarkusTest
@TestProfile(UserResourceAuthTest.Profile.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class UserResourceAuthTest {

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
    static String createdUserId;

    // ── setup ─────────────────────────────────────────────────────

    @Test @Order(1)
    void setupNonAdminKey() {
        QuarkusTransaction.run(() -> {
            User user = userService.create("plain-user", "secret", "user");
            nonAdminKey = apiKeyService.create(user.id, "plain-user-key", null, null).rawKey;
        });
        Assertions.assertNotNull(nonAdminKey, "non-admin API key should have been created");
    }

    // ── unauthenticated ───────────────────────────────────────────

    @Test @Order(2)
    void noHeaderReturns401() {
        given().when().get("/api/users").then().statusCode(401);
    }

    // ── admin (master key) ────────────────────────────────────────

    @Test @Order(3)
    void adminCanListUsers() {
        given()
            .header("Authorization", "Bearer test-master-key")
            .when().get("/api/users")
            .then().statusCode(200)
            .body("size()", greaterThanOrEqualTo(1));
    }

    @Test @Order(4)
    void adminCanCreateUser() {
        createdUserId = given()
            .header("Authorization", "Bearer test-master-key")
            .contentType(ContentType.JSON)
            .body(Map.of("username", "created-by-admin", "password", "secret", "role", "user"))
            .when().post("/api/users")
            .then().statusCode(201)
            .body("username", is("created-by-admin"))
            .extract().path("id");
        Assertions.assertNotNull(createdUserId);
    }

    @Test @Order(5)
    void adminCannotCreateDuplicateUser() {
        given()
            .header("Authorization", "Bearer test-master-key")
            .contentType(ContentType.JSON)
            .body(Map.of("username", "created-by-admin", "password", "secret"))
            .when().post("/api/users")
            .then().statusCode(409);
    }

    @Test @Order(6)
    void adminCreateMissingPasswordIs400() {
        given()
            .header("Authorization", "Bearer test-master-key")
            .contentType(ContentType.JSON)
            .body(Map.of("username", "no-password-here"))
            .when().post("/api/users")
            .then().statusCode(400);
    }

    // ── non-admin: forbidden on every admin operation ─────────────

    @Test @Order(7)
    void nonAdminCannotListUsers() {
        given()
            .header("Authorization", "Bearer " + nonAdminKey)
            .when().get("/api/users")
            .then().statusCode(403)
            .body("error", is("FORBIDDEN"));
    }

    @Test @Order(8)
    void nonAdminCannotCreateUser() {
        given()
            .header("Authorization", "Bearer " + nonAdminKey)
            .contentType(ContentType.JSON)
            .body(Map.of("username", "sneaky", "password", "secret", "role", "admin"))
            .when().post("/api/users")
            .then().statusCode(403);
    }

    @Test @Order(9)
    void nonAdminGets403NotValidationErrors() {
        // GUARD ORDERING REGRESSION TEST. An empty body would be a 400 if the
        // authorization check ran after validation. It must be 403: a non-admin
        // learns nothing about which fields are malformed.
        given()
            .header("Authorization", "Bearer " + nonAdminKey)
            .contentType(ContentType.JSON)
            .body(Map.of())
            .when().post("/api/users")
            .then().statusCode(403);
    }

    @Test @Order(10)
    void nonAdminCannotDeleteUser() {
        given()
            .header("Authorization", "Bearer " + nonAdminKey)
            .when().delete("/api/users/" + createdUserId)
            .then().statusCode(403);
    }

    // ── admin delete ──────────────────────────────────────────────

    @Test @Order(11)
    void adminCanDeleteUser() {
        given()
            .header("Authorization", "Bearer test-master-key")
            .when().delete("/api/users/" + createdUserId)
            .then().statusCode(204);

        given()
            .header("Authorization", "Bearer test-master-key")
            .when().get("/api/users")
            .then().statusCode(200)
            .body("username", not(hasItem("created-by-admin")));
    }

    @Test @Order(12)
    void adminDeleteUnknownUserIs404() {
        given()
            .header("Authorization", "Bearer test-master-key")
            .when().delete("/api/users/NONEXISTENT")
            .then().statusCode(404);
    }
}
