package pl.perigee.gauge.resource;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.*;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.*;

/**
 * Default test profile: gauge.auth.enabled=false, so SecurityFilter is a no-op
 * and no SecurityContext is ever installed. AdminResource.isAdmin() therefore
 * sees a null context and answers 403.
 *
 * That is "fails closed with no identity" — NOT a role check. These two cases
 * prove nothing about authorization; they only pin the unauthenticated answer.
 * The real coverage lives in AdminResourceAuthTest, which runs with auth on.
 */
@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AdminResourceTest {

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
