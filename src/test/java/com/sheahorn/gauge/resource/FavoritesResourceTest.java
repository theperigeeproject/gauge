package pl.perigee.gauge.resource;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.*;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.*;

@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FavoritesResourceTest {

    static String projectId;

    @Test @Order(1)
    void setup() {
        projectId = given().contentType(ContentType.JSON)
            .body(Map.of("name", "FavTest Project"))
            .when().post("/api/projects").then().statusCode(201).extract().path("id");
    }

    @Test @Order(2)
    void list() {
        // FavoritesResource requires auth; in test mode with security disabled,
        // SecurityContext is null → 401
        given().when().get("/api/favorites").then().statusCode(401);
    }

    @Test @Order(3)
    void add() {
        // POST without ContentType → 415
        given().when().post("/api/favorites/" + projectId).then().statusCode(415);
    }

    @Test @Order(4)
    void addWithContentType() {
        // POST with ContentType but no auth → 401
        given().contentType(ContentType.JSON)
            .when().post("/api/favorites/" + projectId).then().statusCode(401);
    }

    @Test @Order(5)
    void remove() {
        given().when().delete("/api/favorites/" + projectId).then().statusCode(401);
    }

    @Test @Order(6)
    void reset() {
        given().when().delete("/api/favorites").then().statusCode(401);
    }

    @Test @Order(7)
    void cleanup() {
        given().when().delete("/api/projects/" + projectId + "?cascade=true");
    }
}
