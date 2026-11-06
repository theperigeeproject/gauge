package pl.perigee.gauge.resource;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.*;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.*;

@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AdminResourceTest {

    // @RolesAllowed("admin") on the class — in test mode with security disabled,
    // there's no authenticated identity → 403 for all endpoints.

    @Test @Order(1)
    void migrateIdsWithoutConfirm() {
        given().contentType(ContentType.JSON)
            .when().post("/api/admin/migrate-ids")
            .then().statusCode(403);
    }

    @Test @Order(2)
    void migrateIdsWithConfirm() {
        given().contentType(ContentType.JSON)
            .when().post("/api/admin/migrate-ids?confirm=true")
            .then().statusCode(403);
    }
}
