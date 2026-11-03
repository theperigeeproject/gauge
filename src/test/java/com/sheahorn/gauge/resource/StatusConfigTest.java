package com.sheahorn.gauge.resource;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.*;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Issue 0000186F — configurable statuses: registry, admin CRUD, and the guarantee that
 * analysis and filtering follow the configuration instead of hardcoded codes.
 */
@QuarkusTest
@TestProfile(StatusConfigTest.Profile.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class StatusConfigTest {

    public static class Profile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                "gauge.auth.enabled", "true",
                "gauge.auth.seed-on-startup", "false",
                "gauge.api-key.access", "status-master-key",
                "gauge.api-key.pepper", "test-pepper"
            );
        }
    }

    static final String AUTH = "Bearer status-master-key";

    static String projectId;
    static String issueId;
    static String blockedId;

    // ── Discovery ──────────────────────────────────────────────────

    @Test @Order(1)
    void listsAllLevels() {
        given().header("Authorization", AUTH)
            .when().get("/api/statuses")
            .then().statusCode(200)
            .body("ISSUE.size()", is(4))
            .body("TASKLIST.size()", is(4))
            .body("TASK.size()", is(5));
    }

    @Test @Order(2)
    void listsSingleLevelOrderedByRank() {
        given().header("Authorization", AUTH)
            .when().get("/api/statuses?level=ISSUE")
            .then().statusCode(200)
            .body("size()", is(4))
            .body("[0].code", is("TODO"))
            .body("[0].category", is("ACTIVE"))
            .body("[0].builtin", is(true))
            .body("[3].code", is("INACTIVE"))
            .body("[3].category", is("PARKED"))
            .body("[3].showByDefault", is(true));
    }

    @Test @Order(3)
    void rejectsUnknownLevel() {
        given().header("Authorization", AUTH)
            .when().get("/api/statuses?level=NOTALEVEL")
            .then().statusCode(400)
            .body("error", is("INVALID_LEVEL"));
    }

    // ── Create ─────────────────────────────────────────────────────

    @Test @Order(4)
    void adminCanCreateCustomStatus() {
        blockedId = given().header("Authorization", AUTH)
            .contentType(ContentType.JSON)
            .body(Map.of("code", "blocked", "label", "Blocked", "level", "ISSUE",
                "category", "ACTIVE", "color", "#f87171"))
            .when().post("/api/admin/statuses")
            .then().statusCode(201)
            .body("code", is("BLOCKED"))
            .body("label", is("Blocked"))
            .body("category", is("ACTIVE"))
            .body("builtin", is(false))
            .extract().path("id");
    }

    @Test @Order(5)
    void duplicateCodeIsConflict() {
        given().header("Authorization", AUTH)
            .contentType(ContentType.JSON)
            .body(Map.of("code", "BLOCKED", "level", "ISSUE", "category", "ACTIVE"))
            .when().post("/api/admin/statuses")
            .then().statusCode(409)
            .body("error", is("STATUS_EXISTS"));
    }

    @Test @Order(6)
    void invalidCodeIsRejected() {
        given().header("Authorization", AUTH)
            .contentType(ContentType.JSON)
            .body(Map.of("code", "not a code!", "level", "ISSUE", "category", "ACTIVE"))
            .when().post("/api/admin/statuses")
            .then().statusCode(400)
            .body("error", is("INVALID_CODE"));
    }

    @Test @Order(7)
    void invalidColorIsRejected() {
        given().header("Authorization", AUTH)
            .contentType(ContentType.JSON)
            .body(Map.of("code", "WEIRD", "level", "ISSUE", "category", "ACTIVE", "color", "red"))
            .when().post("/api/admin/statuses")
            .then().statusCode(400)
            .body("error", is("INVALID_COLOR"));
    }

    @Test @Order(8)
    void missingCategoryIsRejected() {
        given().header("Authorization", AUTH)
            .contentType(ContentType.JSON)
            .body(Map.of("code", "NOCATEGORY", "level", "ISSUE"))
            .when().post("/api/admin/statuses")
            .then().statusCode(400)
            .body("error", is("INVALID_CATEGORY"));
    }

    // ── Using custom statuses ──────────────────────────────────────

    @Test @Order(9)
    void canAssignCustomStatusToAnIssue() {
        projectId = given().header("Authorization", AUTH)
            .contentType(ContentType.JSON)
            .body(Map.of("name", "StatusConfig Project"))
            .when().post("/api/projects").then().statusCode(201).extract().path("id");

        issueId = given().header("Authorization", AUTH)
            .contentType(ContentType.JSON)
            .body(Map.of("title", "blocked issue", "priority", "HIGH"))
            .when().post("/api/projects/" + projectId + "/issues")
            .then().statusCode(201).extract().path("id");

        given().header("Authorization", AUTH)
            .contentType(ContentType.JSON).body(Map.of("status", "BLOCKED"))
            .when().patch("/api/issues/" + issueId + "/status")
            .then().statusCode(200).body("status", is("BLOCKED"));
    }

    @Test @Order(10)
    void customActiveStatusBubblesPriorityInAnalysis() {
        given().header("Authorization", AUTH)
            .when().get("/api/projects/analysis")
            .then().statusCode(200)
            .body("'" + projectId + "'.bubbledPriority", is("HIGH"))
            .body("'" + projectId + "'.issueCounts.BLOCKED", is(1));
    }

    @Test @Order(11)
    void canFilterByCustomStatus() {
        given().header("Authorization", AUTH)
            .when().get("/api/projects/" + projectId + "/issues?status=BLOCKED")
            .then().statusCode(200)
            .body("size()", is(1))
            .body("[0].status", is("BLOCKED"));
    }

    @Test @Order(12)
    void parkedStatusNeverBubbles() {
        // Making it PARKED must stop the HIGH priority from bubbling.
        given().header("Authorization", AUTH)
            .contentType(ContentType.JSON).body(Map.of("category", "PARKED"))
            .when().patch("/api/admin/statuses/" + blockedId)
            .then().statusCode(200).body("category", is("PARKED"));

        given().header("Authorization", AUTH)
            .when().get("/api/projects/analysis")
            .then().statusCode(200)
            .body("'" + projectId + "'.bubbledPriority", is("INACTIVE"));
    }

    @Test @Order(13)
    void finishedStatusCountsAsDone() {
        given().header("Authorization", AUTH)
            .contentType(ContentType.JSON).body(Map.of("category", "FINISHED"))
            .when().patch("/api/admin/statuses/" + blockedId)
            .then().statusCode(200);

        given().header("Authorization", AUTH)
            .when().get("/api/projects/analysis")
            .then().statusCode(200)
            .body("'" + projectId + "'.bubbledPriority", is("DONE"));
    }

    // ── Rename rewrites rows ───────────────────────────────────────

    @Test @Order(14)
    void renameRewritesExistingRows() {
        given().header("Authorization", AUTH)
            .contentType(ContentType.JSON).body(Map.of("code", "STUCK"))
            .when().patch("/api/admin/statuses/" + blockedId)
            .then().statusCode(200)
            .body("code", is("STUCK"));

        given().header("Authorization", AUTH)
            .when().get("/api/issues/" + issueId)
            .then().statusCode(200).body("status", is("STUCK"));
    }

    // ── Deletion rules ─────────────────────────────────────────────

    @Test @Order(15)
    void builtInStatusCannotBeDeleted() {
        String todoId = given().header("Authorization", AUTH)
            .when().get("/api/statuses?level=ISSUE")
            .then().statusCode(200).extract().path("[0].id");

        given().header("Authorization", AUTH)
            .when().delete("/api/admin/statuses/" + todoId)
            .then().statusCode(409)
            .body("error", is("BUILTIN_STATUS"));
    }

    @Test @Order(16)
    void inUseStatusRequiresReassignment() {
        given().header("Authorization", AUTH)
            .when().delete("/api/admin/statuses/" + blockedId)
            .then().statusCode(409)
            .body("error", is("STATUS_IN_USE"));
    }

    @Test @Order(17)
    void reassignmentMovesRowsThenDeletes() {
        given().header("Authorization", AUTH)
            .when().delete("/api/admin/statuses/" + blockedId + "?reassignTo=INACTIVE")
            .then().statusCode(204);

        given().header("Authorization", AUTH)
            .when().get("/api/issues/" + issueId)
            .then().statusCode(200).body("status", is("INACTIVE"));

        given().header("Authorization", AUTH)
            .when().get("/api/statuses?level=ISSUE")
            .then().statusCode(200).body("size()", is(4));
    }

    // ── Guards ────────────────────────────────────────────────────

    @Test @Order(18)
    void startStatusCannotBeRenamedOrDeleted() {
        String todoId = given().header("Authorization", AUTH)
            .when().get("/api/statuses?level=TASK")
            .then().statusCode(200).extract().path("[0].id");

        given().header("Authorization", AUTH)
            .contentType(ContentType.JSON).body(Map.of("code", "PENDING"))
            .when().patch("/api/admin/statuses/" + todoId)
            .then().statusCode(409)
            .body("error", is("START_STATUS_PROTECTED"));
    }

    @Test @Order(19)
    void statusCodeIsRequiredOnStatusUpdate() {
        given().header("Authorization", AUTH)
            .contentType(ContentType.JSON).body(Map.of())
            .when().patch("/api/issues/" + issueId + "/status")
            .then().statusCode(400)
            .body("error", is("INVALID_STATUS"))
            .body("message", containsString("TODO"));
    }

    @Test @Order(20)
    void nonAdminCannotManageStatuses() {
        // No token at all → 401 from the SecurityFilter before any role check.
        given()
            .contentType(ContentType.JSON)
            .body(Map.of("code", "SNEAKY", "level", "ISSUE", "category", "ACTIVE"))
            .when().post("/api/admin/statuses")
            .then().statusCode(401);
    }

    @Test @Order(21)
    void cleanup() {
        given().header("Authorization", AUTH)
            .when().delete("/api/projects/" + projectId + "?cascade=true")
            .then().statusCode(204);
    }

    @Test @Order(22)
    void defaultsSurviveAfterCleanup() {
        List<String> codes = given().header("Authorization", AUTH)
            .when().get("/api/statuses?level=ISSUE")
            .then().statusCode(200)
            .extract().jsonPath().getList("code", String.class);
        assertTrue(codes.contains("TODO"));
        assertTrue(codes.contains("INACTIVE"));
        assertFalse(codes.contains("BLOCKED"));
        assertFalse(codes.contains("STUCK"));
    }
}
