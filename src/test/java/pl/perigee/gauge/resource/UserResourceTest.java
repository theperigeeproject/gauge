package pl.perigee.gauge.resource;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.*;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.*;

@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class UserResourceTest {

    // In test mode with security disabled:
    // - /api/users/me: SecurityContext is null → 401
    // - /api/users (GET): @RolesAllowed("admin") → 403
    // - /api/users (POST): @RolesAllowed("admin") → 403
    // - /api/users/{id}/password: blank password → 400 (checked before auth);
    //   non-blank password with null SecurityContext → 403
    // - /api/users/{id} (DELETE): @RolesAllowed("admin") → 403

    @Test @Order(1)
    void me() {
        given().when().get("/api/users/me").then().statusCode(401);
    }

    @Test @Order(2)
    void list() {
        given().when().get("/api/users").then().statusCode(403);
    }

    @Test @Order(3)
    void create() {
        given().contentType(ContentType.JSON)
            .body(Map.of("username", "testuser", "password", "secret", "role", "user"))
            .when().post("/api/users").then().statusCode(403);
    }

    @Test @Order(4)
    void createMissingUsername() {
        given().contentType(ContentType.JSON).body(Map.of("password", "secret"))
            .when().post("/api/users").then().statusCode(403);
    }

    @Test @Order(5)
    void createMissingPassword() {
        given().contentType(ContentType.JSON).body(Map.of("username", "nopass"))
            .when().post("/api/users").then().statusCode(403);
    }

    @Test @Order(6)
    void createDuplicate() {
        given().contentType(ContentType.JSON)
            .body(Map.of("username", "testuser", "password", "secret"))
            .when().post("/api/users").then().statusCode(403);
    }

    @Test @Order(7)
    void changePassword() {
        given().contentType(ContentType.JSON).body(Map.of("password", "newsecret"))
            .when().patch("/api/users/any-id/password").then().statusCode(403);
    }

    @Test @Order(8)
    void changePasswordBlank() {
        // Blank password is checked BEFORE auth → 400, not 403
        given().contentType(ContentType.JSON).body(Map.of("password", ""))
            .when().patch("/api/users/any-id/password").then().statusCode(400);
    }

    @Test @Order(9)
    void changePasswordNotFound() {
        given().contentType(ContentType.JSON).body(Map.of("password", "x"))
            .when().patch("/api/users/NONEXISTENT/password").then().statusCode(403);
    }

    @Test @Order(10)
    void delete() {
        given().when().delete("/api/users/any-id").then().statusCode(403);
    }

    @Test @Order(11)
    void deleteNotFound() {
        given().when().delete("/api/users/NONEXISTENT").then().statusCode(403);
    }
}
