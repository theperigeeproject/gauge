package pl.perigee.gauge.security;

import pl.perigee.gauge.domain.User;
import pl.perigee.gauge.service.ApiKeyService;
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
 * End-to-end security tests with authentication enabled.
 * Verifies the full chain: SecurityFilter → ApiKeyResolver → ProjectAccessGuard.
 */
@QuarkusTest
@TestProfile(SecurityEndToEndTest.Profile.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SecurityEndToEndTest {

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
    ApiKeyService apiKeyService;

    static String projectA;
    static String projectB;
    static String readKey;
    static String writeKey;

    // ── Setup ─────────────────────────────────────────────────────

    @Test @Order(1)
    void setupProjects() {
        projectA = given()
            .header("Authorization", "Bearer test-master-key")
            .contentType(ContentType.JSON)
            .body(Map.of("name", "Project A (scoped)"))
            .when().post("/api/projects")
            .then().statusCode(201)
            .extract().path("id");

        projectB = given()
            .header("Authorization", "Bearer test-master-key")
            .contentType(ContentType.JSON)
            .body(Map.of("name", "Project B (outside)"))
            .when().post("/api/projects")
            .then().statusCode(201)
            .extract().path("id");
    }

    @Test @Order(2)
    void setupApiKeys() {
        QuarkusTransaction.run(() -> {
            User admin = User.findByUsername("admin");
            Assertions.assertNotNull(admin, "admin user should be seeded");

            ApiKeyService.CreateResult readResult = apiKeyService.create(
                admin.id, "e2e-read", projectA, "READ");
            readKey = readResult.rawKey;

            ApiKeyService.CreateResult writeResult = apiKeyService.create(
                admin.id, "e2e-write", projectA, "WRITE");
            writeKey = writeResult.rawKey;
        });
    }

    // ── Unauthenticated ───────────────────────────────────────────

    @Test @Order(3)
    void unauthenticatedApiCallReturns401() {
        given()
            .when().get("/api/projects")
            .then().statusCode(401);
    }

    @Test @Order(4)
    void unauthenticatedUiCallReturns403() {
        // Quarkus HTTP permission layer catches / → 403 (authenticated policy)
        given()
            .when().get("/")
            .then().statusCode(403);
    }

    @Test @Order(5)
    void unauthenticatedUiProjectsReturns403() {
        // Quarkus HTTP permission layer catches /ui/* → 403 (authenticated policy)
        given()
            .when().get("/ui/projects")
            .then().statusCode(403);
    }

    // ── Public paths ──────────────────────────────────────────────

    @Test @Order(6)
    void loginPageIsPublic() {
        given()
            .when().get("/login.html")
            .then().statusCode(200);
    }

    @Test @Order(7)
    void loginFailedPageIsPublic() {
        given()
            .when().get("/login-failed.html")
            .then().statusCode(200);
    }

    // ── Invalid tokens ────────────────────────────────────────────

    @Test @Order(8)
    void invalidTokenReturns401() {
        given()
            .header("Authorization", "Bearer garbage-token")
            .when().get("/api/projects")
            .then().statusCode(401);
    }

    @Test @Order(9)
    void emptyTokenReturns401() {
        given()
            .header("Authorization", "Bearer ")
            .when().get("/api/projects")
            .then().statusCode(401);
    }

    @Test @Order(10)
    void noAuthHeaderReturns401() {
        given()
            .when().get("/api/projects/" + projectA)
            .then().statusCode(401);
    }

    // ── Master key ────────────────────────────────────────────────

    @Test @Order(11)
    void masterKeyCanListAllProjects() {
        given()
            .header("Authorization", "Bearer test-master-key")
            .when().get("/api/projects")
            .then().statusCode(200)
            .body("size()", not(0));
    }

    @Test @Order(12)
    void masterKeyCanAccessAnyProject() {
        given()
            .header("Authorization", "Bearer test-master-key")
            .when().get("/api/projects/" + projectB)
            .then().statusCode(200);
    }

    @Test @Order(13)
    void masterKeyCanWrite() {
        given()
            .header("Authorization", "Bearer test-master-key")
            .contentType(ContentType.JSON)
            .body(Map.of("title", "master write test"))
            .when().post("/api/projects/" + projectA + "/issues")
            .then().statusCode(201);
    }

    // ── READ key: can read scoped, cannot read outside, cannot write

    @Test @Order(14)
    void readKeyCanAccessScopedProject() {
        given()
            .header("Authorization", "Bearer " + readKey)
            .when().get("/api/projects/" + projectA)
            .then().statusCode(200);
    }

    @Test @Order(15)
    void readKeyCannotAccessOtherProject() {
        given()
            .header("Authorization", "Bearer " + readKey)
            .when().get("/api/projects/" + projectB)
            .then().statusCode(403);
    }

    @Test @Order(16)
    void readKeyListFiltersToScopedOnly() {
        given()
            .header("Authorization", "Bearer " + readKey)
            .when().get("/api/projects?flat=true")
            .then().statusCode(200)
            .body("size()", is(1))
            .body("[0].id", is(projectA));
    }

    @Test @Order(17)
    void readKeyCannotWrite() {
        given()
            .header("Authorization", "Bearer " + readKey)
            .contentType(ContentType.JSON)
            .body(Map.of("title", "read key write attempt"))
            .when().post("/api/projects/" + projectA + "/issues")
            .then().statusCode(403);
    }

    @Test @Order(18)
    void readKeyCannotPatch() {
        given()
            .header("Authorization", "Bearer " + readKey)
            .contentType(ContentType.JSON)
            .body(Map.of("name", "hacked"))
            .when().patch("/api/projects/" + projectA)
            .then().statusCode(403);
    }

    // ── WRITE key: can read and write scoped, cannot access outside

    @Test @Order(19)
    void writeKeyCanAccessScopedProject() {
        given()
            .header("Authorization", "Bearer " + writeKey)
            .when().get("/api/projects/" + projectA)
            .then().statusCode(200);
    }

    @Test @Order(20)
    void writeKeyCannotAccessOtherProject() {
        given()
            .header("Authorization", "Bearer " + writeKey)
            .when().get("/api/projects/" + projectB)
            .then().statusCode(403);
    }

    @Test @Order(21)
    void writeKeyCanWrite() {
        String issueId = given()
            .header("Authorization", "Bearer " + writeKey)
            .contentType(ContentType.JSON)
            .body(Map.of("title", "write key test"))
            .when().post("/api/projects/" + projectA + "/issues")
            .then().statusCode(201)
            .extract().path("id");

        // Clean up
        given()
            .header("Authorization", "Bearer test-master-key")
            .when().delete("/api/issues/" + issueId)
            .then().statusCode(204);
    }

    @Test @Order(22)
    void writeKeyCannotWriteOtherProject() {
        given()
            .header("Authorization", "Bearer " + writeKey)
            .contentType(ContentType.JSON)
            .body(Map.of("title", "write key cross-project"))
            .when().post("/api/projects/" + projectB + "/issues")
            .then().statusCode(403);
    }

    // ── Cleanup ───────────────────────────────────────────────────

    @Test @Order(23)
    void cleanup() {
        given()
            .header("Authorization", "Bearer test-master-key")
            .when().delete("/api/projects/" + projectA + "?cascade=true");

        given()
            .header("Authorization", "Bearer test-master-key")
            .when().delete("/api/projects/" + projectB + "?cascade=true");
    }
}
