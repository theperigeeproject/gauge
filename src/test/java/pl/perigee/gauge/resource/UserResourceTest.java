package pl.perigee.gauge.resource;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.*;

import java.util.Map;

import static io.restassured.RestAssured.given;

/**
 * Default test profile: gauge.auth.enabled=false. SecurityFilter is a no-op and
 * no SecurityContext is installed, so UserResource.isAdmin() sees a null context
 * and answers 403.
 *
 * These cases therefore pin the "no identity" answers, not role enforcement.
 * Real authorization coverage is UserResourceAuthTest, which runs with auth on.
 *
 * changePassword's blank-password 400 happens BEFORE the admin check. That is a
 * pre-existing ordering, kept deliberately and not part of this change.
 */
@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class UserResourceTest {

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
