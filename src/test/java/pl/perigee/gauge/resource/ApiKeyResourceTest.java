package pl.perigee.gauge.resource;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.*;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.*;

@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ApiKeyResourceTest {

    // In test mode with security disabled, SecurityContext is null.
    // ApiKeyResource.getCurrentUserId() returns null → 401 for all endpoints.
    // These tests verify the auth gate, not the resource logic (which is
    // covered by ApiKeyServiceTest).

    @Test @Order(1)
    void list() {
        given().when().get("/api/apikeys").then().statusCode(401);
    }

    @Test @Order(2)
    void create() {
        given().contentType(ContentType.JSON).body(Map.of("name", "Test Key"))
            .when().post("/api/apikeys").then().statusCode(401);
    }

    @Test @Order(3)
    void createEmptyName() {
        given().contentType(ContentType.JSON).body(Map.of("name", ""))
            .when().post("/api/apikeys").then().statusCode(401);
    }

    @Test @Order(4)
    void createNoName() {
        given().contentType(ContentType.JSON).body(Map.of())
            .when().post("/api/apikeys").then().statusCode(401);
    }

    @Test @Order(5)
    void delete() {
        given().when().delete("/api/apikeys/any-id").then().statusCode(401);
    }

    @Test @Order(6)
    void deleteNotFound() {
        given().when().delete("/api/apikeys/NONEXISTENT").then().statusCode(401);
    }
}
