package com.sheahorn.gauge.resource;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;

/**
 * Issue 0000149C — {@code gauge.issue.description.allow-long=true} lifts the 4000-char cap on
 * both write paths, up to the database limit.
 */
@QuarkusTest
@TestProfile(IssueDescriptionLongAllowedTest.LongAllowedProfile.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class IssueDescriptionLongAllowedTest {

    public static class LongAllowedProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("gauge.issue.description.allow-long", "true");
        }
    }

    static String projectId;
    static String issueId;

    @Test @Order(1)
    void setup() {
        projectId = given().contentType(ContentType.JSON)
            .body(Map.of("name", "LongDesc Project"))
            .when().post("/api/projects").then().statusCode(201).extract().path("id");
        issueId = given().contentType(ContentType.JSON)
            .body(Map.of("title", "LongDesc Issue", "description", "short"))
            .when().post("/api/projects/" + projectId + "/issues")
            .then().statusCode(201).extract().path("id");
    }

    @Test @Order(2)
    void createAcceptsLongDescription() {
        given().contentType(ContentType.JSON)
            .body(Map.of("title", "long-create", "description", "x".repeat(40_000)))
            .when().post("/api/projects/" + projectId + "/issues")
            .then().statusCode(201);
    }

    @Test @Order(3)
    void patchAcceptsLongDescription() {
        given().contentType(ContentType.JSON)
            .body(Map.of("description", "y".repeat(40_000)))
            .when().patch("/api/issues/" + issueId)
            .then().statusCode(200);
    }

    @Test @Order(4)
    void hardDatabaseLimitStillRejectedWith400() {
        given().contentType(ContentType.JSON)
            .body(Map.of("title", "way-too-long", "description", "z".repeat(1_000_001)))
            .when().post("/api/projects/" + projectId + "/issues")
            .then().statusCode(400)
            .body("error", is("DESCRIPTION_TOO_LONG"))
            .body("message", containsString("1000000"));
    }

    @Test @Order(5)
    void cleanup() {
        given().when().delete("/api/projects/" + projectId + "?cascade=true").then().statusCode(204);
    }
}
