package pl.perigee.gauge.security;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;

@QuarkusTest
@TestProfile(SecurityFilterTest.Profile.class)
class SecurityFilterTest {

    public static class Profile implements io.quarkus.test.junit.QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                "gauge.auth.enabled", "true",
                "gauge.auth.seed-on-startup", "true",
                "gauge.auth.admin.username", "admin",
                "gauge.auth.admin.password", "admin",
                "gauge.api-key.access", "test-key",
                "gauge.api-key.pepper", "test-pepper"
            );
        }
    }

    @Test
    void testExactPathApiReturns404() {
        // /api has no resource mapped and no Quarkus HTTP permission rule
        // matches it exactly (only /api/*), so it falls through to 404.
        given()
            .when().get("/api")
            .then()
            .statusCode(404);
    }

    @Test
    void testApiPathWithBearerEmptyToken() {
        given()
            .header("Authorization", "Bearer ")
            .when().get("/api/projects")
            .then()
            .statusCode(401);
    }

    @Test
    void testApiPathWithBearerValidToken() {
        given()
            .header("Authorization", "Bearer test-key")
            .when().get("/api/projects")
            .then()
            .statusCode(200);
    }

    @Test
    void testApiPathWithBearerInvalidToken() {
        given()
            .header("Authorization", "Bearer invalid-token")
            .when().get("/api/projects")
            .then()
            .statusCode(401);
    }

    @Test
    void testApiPathWithoutAuthHeader() {
        given()
            .when().get("/api/projects")
            .then()
            .statusCode(401);
    }

    @Test
    void testLoginPageDoesNotRequireAuth() {
        given()
            .when().get("/login.html")
            .then()
            .statusCode(200);
    }

    @Test
    void testLoginFailedPageDoesNotRequireAuth() {
        given()
            .when().get("/login-failed.html")
            .then()
            .statusCode(200);
    }

    @Test
    void testUiPathRequiresAuth() {
        // Quarkus HTTP permission layer catches this first → 403
        given()
            .when().get("/ui/projects")
            .then()
            .statusCode(403);
    }

    @Test
    void testRootPathRequiresAuth() {
        // Quarkus HTTP permission layer catches this first → 403
        given()
            .when().get("/")
            .then()
            .statusCode(403);
    }
}
