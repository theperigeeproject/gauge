package pl.perigee.gauge.resource;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.*;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.*;

/**
 * The description-length contract, default profile: {@code gauge.issue.description.allow-long=false},
 * so the cap is 4000 characters.
 *
 * <p>Before this existed the cap lived only in the column definition, so an over-long description
 * produced a 500 with an H2 stack trace on BOTH write paths. At least one case here must fail if
 * that ever returns.
 */
@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class IssueDescriptionLimitTest {

    static final String AT_LIMIT = "x".repeat(4000);
    static final String OVER_LIMIT = "x".repeat(4001);

    static String projectId;
    static String issueId;

    @Test @Order(1)
    void setup() {
        projectId = given().contentType(ContentType.JSON)
            .body(Map.of("name", "Description Limit Project"))
            .when().post("/api/projects")
            .then().statusCode(201).extract().path("id");
    }

    @Test @Order(2)
    void createAtTheLimitIsAccepted() {
        issueId = given().contentType(ContentType.JSON)
            .body(Map.of("title", "at limit", "description", AT_LIMIT))
            .when().post("/api/projects/" + projectId + "/issues")
            .then().statusCode(201)
            .extract().path("id");
    }

    @Test @Order(3)
    void createOverTheLimitIs400AndCreatesNoRow() {
        int before = given().when().get("/api/issues").then().statusCode(200)
            .extract().jsonPath().getList("$").size();

        given().contentType(ContentType.JSON)
            .body(Map.of("title", "over limit", "description", OVER_LIMIT))
            .when().post("/api/projects/" + projectId + "/issues")
            .then().statusCode(400)
            .body("error", is("DESCRIPTION_TOO_LONG"))
            .body("message", containsString("4001"))
            .body("message", containsString("4000"));

        int after = given().when().get("/api/issues").then().statusCode(200)
            .extract().jsonPath().getList("$").size();

        Assertions.assertEquals(before, after, "a rejected create must not leave a row");
    }

    @Test @Order(4)
    void patchOverTheLimitIs400() {
        given().contentType(ContentType.JSON)
            .body(Map.of("description", OVER_LIMIT))
            .when().patch("/api/issues/" + issueId)
            .then().statusCode(400)
            .body("error", is("DESCRIPTION_TOO_LONG"));
    }

    @Test @Order(5)
    void patchAtTheLimitIsAccepted() {
        given().contentType(ContentType.JSON)
            .body(Map.of("description", AT_LIMIT))
            .when().patch("/api/issues/" + issueId)
            .then().statusCode(200);
    }

    @Test @Order(6)
    void messageNamesTheLimitInForceAndHowToRaiseIt() {
        given().contentType(ContentType.JSON)
            .body(Map.of("title", "why", "description", OVER_LIMIT))
            .when().post("/api/projects/" + projectId + "/issues")
            .then().statusCode(400)
            .body("message", containsString("gauge.issue.description.allow-long=true"));
    }

    // ── enum-ish query params: rejected, not silently dropped (0000149B) ──────

    @Test @Order(7)
    void unknownPriorityFilterIsRejected() {
        // Order-insensitive on purpose: the message lists enum declaration order, which is
        // irrelevant to the caller. What matters is that every valid value is named.
        given().when().get("/api/projects/" + projectId + "/issues?priority=MEDIUM")
            .then().statusCode(400)
            .body("error", is("INVALID_PRIORITY"))
            .body("message", containsString("HIGH"))
            .body("message", containsString("NORMAL"))
            .body("message", containsString("LOW"));
    }

    @Test @Order(8)
    void unknownStatusFilterIsRejected() {
        given().when().get("/api/projects/" + projectId + "/issues?status=BOGUS")
            .then().statusCode(400)
            .body("error", is("INVALID_STATUS"))
            .body("message", containsString("TODO"))
            .body("message", containsString("DOING"))
            .body("message", containsString("DONE"))
            .body("message", containsString("INACTIVE"));
    }

    @Test @Order(9)
    void knownFiltersAreCaseInsensitiveAndStillWork() {
        given().when().get("/api/projects/" + projectId + "/issues?priority=  high  &status=todo")
            .then().statusCode(200);
    }

    @Test @Order(10)
    void cleanup() {
        given().when().delete("/api/projects/" + projectId + "?cascade=true");
    }
}
