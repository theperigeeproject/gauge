package com.sheahorn.gauge.resource;

import io.quarkus.test.junit.QuarkusTest;
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
 * Issue 0000149B — POST and PATCH must apply the same description cap, and both must fail
 * with a 400 that names the limit. Invalid enum values must never be silently dropped.
 */
@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class IssueDescriptionLimitTest {

    static String projectId;
    static String issueId;

    @Test @Order(1)
    void setup() {
        projectId = given().contentType(ContentType.JSON)
            .body(Map.of("name", "DescLimit Project"))
            .when().post("/api/projects").then().statusCode(201).extract().path("id");
        issueId = given().contentType(ContentType.JSON)
            .body(Map.of("title", "DescLimit Issue", "description", "short"))
            .when().post("/api/projects/" + projectId + "/issues")
            .then().statusCode(201).extract().path("id");
    }

    @Test @Order(2)
    void createAcceptsExactlyTheLimit() {
        given().contentType(ContentType.JSON)
            .body(Map.of("title", "at-limit", "description", "x".repeat(4000)))
            .when().post("/api/projects/" + projectId + "/issues")
            .then().statusCode(201);
    }

    @Test @Order(3)
    void createRejectsOverTheLimitWith400() {
        given().contentType(ContentType.JSON)
            .body(Map.of("title", "too-long", "description", "x".repeat(4001)))
            .when().post("/api/projects/" + projectId + "/issues")
            .then().statusCode(400)
            .body("error", is("DESCRIPTION_TOO_LONG"))
            .body("message", containsString("4000"));
    }

    @Test @Order(4)
    void rejectedCreateDoesNotCreateARow() {
        String marker = "phantom-row-" + System.nanoTime();
        given().contentType(ContentType.JSON)
            .body(Map.of("title", marker, "description", "x".repeat(4001)))
            .when().post("/api/projects/" + projectId + "/issues")
            .then().statusCode(400);

        // /api/issues?q= is the search endpoint; the per-project list has no q param.
        given().when().get("/api/issues?q=" + marker)
            .then().statusCode(200).body("size()", is(0));
    }

    @Test @Order(5)
    void patchRejectsOverTheLimitWith400() {
        given().contentType(ContentType.JSON)
            .body(Map.of("description", "x".repeat(4001)))
            .when().patch("/api/issues/" + issueId)
            .then().statusCode(400)
            .body("error", is("DESCRIPTION_TOO_LONG"))
            .body("message", containsString("4000"));
    }

    @Test @Order(6)
    void invalidPriorityIsRejectedWithValidValues() {
        given().contentType(ContentType.JSON)
            .body(Map.of("title", "bad-prio", "priority", "MEDIUM"))
            .when().post("/api/projects/" + projectId + "/issues")
            .then().statusCode(400)
            .body("error", is("INVALID_PRIORITY"))
            .body("message", containsString("HIGH"))
            .body("message", containsString("NORMAL"))
            .body("message", containsString("LOW"));
    }

    @Test @Order(7)
    void omittedPriorityDefaultsToNormal() {
        given().contentType(ContentType.JSON)
            .body(Map.of("title", "default-prio"))
            .when().post("/api/projects/" + projectId + "/issues")
            .then().statusCode(201).body("priority", is("NORMAL"));
    }

    @Test @Order(8)
    void invalidPriorityOnPatchIsRejectedWithValidValues() {
        given().contentType(ContentType.JSON).body(Map.of("priority", "URGENT"))
            .when().patch("/api/issues/" + issueId + "/priority")
            .then().statusCode(400)
            .body("error", is("INVALID_PRIORITY"))
            .body("message", containsString("HIGH"));
    }

    @Test @Order(9)
    void missingPriorityOnPatchIsRejected() {
        given().contentType(ContentType.JSON).body(Map.of())
            .when().patch("/api/issues/" + issueId + "/priority")
            .then().statusCode(400)
            .body("error", is("INVALID_PRIORITY"));
    }

    @Test @Order(10)
    void invalidStatusIsRejectedWithValidValues() {
        given().contentType(ContentType.JSON).body(Map.of("status", "MEDIUM"))
            .when().patch("/api/issues/" + issueId + "/status")
            .then().statusCode(400)
            .body("error", is("INVALID_STATUS"))
            .body("message", containsString("INACTIVE"));
    }

    @Test @Order(11)
    void statusIsCaseInsensitive() {
        given().contentType(ContentType.JSON).body(Map.of("status", "doing"))
            .when().patch("/api/issues/" + issueId + "/status")
            .then().statusCode(200).body("status", is("DOING"));
    }

    @Test @Order(12)
    void cleanup() {
        given().when().delete("/api/projects/" + projectId + "?cascade=true").then().statusCode(204);
    }
}
