package pl.perigee.gauge.resource;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;

/**
 * {@code GET /api/statuses/{level}} — read-only discovery for the UI (ADR 0020 §12).
 *
 * <p>The load-bearing properties: the <b>code</b> is the identity and no status id leaks; the order
 * is {@code precedenceValue}; the §6 predicates are resolved server-side so nothing downstream has
 * to know what a progress level means.
 */
@QuarkusTest
class StatusResourceTest {

    @Test
    void issueStatusesComeBackInPrecedenceOrder() {
        given().when().get("/api/statuses/issue")
            .then().statusCode(200)
            .body("code", contains("TODO", "DOING", "DONE", "INACTIVE", "NEEDS_MIGRATION"))
            .body("precedenceValue", contains(0, 1, 2, 3, 4));
    }

    @Test
    void noStatusIdAppearsInTheBody() {
        // The id is internal (ADR 0020 §2): a caller that can read it will eventually depend on it.
        given().when().get("/api/statuses/task")
            .then().statusCode(200)
            .body("$", everyItem(not(hasKey("id"))))
            .body("$", everyItem(hasKey("code")));
    }

    @Test
    void theAxesAreResolvedIntoBooleansServerSide() {
        given().when().get("/api/statuses/issue")
            .then().statusCode(200)
            .body("find { it.code == 'DONE' }.terminal", is(true))
            .body("find { it.code == 'DONE' }.active", is(false))
            .body("find { it.code == 'INACTIVE' }.parked", is(true))
            .body("find { it.code == 'INACTIVE' }.terminal", is(false))
            .body("find { it.code == 'TODO' }.active", is(true))
            .body("find { it.code == 'TODO' }.terminal", is(false))
            .body("find { it.code == 'TODO' }.parked", is(false));
    }

    @Test
    void anUnmigratedRowResolvesToTheMigrationStatusNotToAFlag() {
        // NEEDS_MIGRATION is available unconditionally (it is what a null statusId resolves to), so
        // it must be discoverable and must read as active so it keeps counting and bubbling.
        given().when().get("/api/statuses/issue")
            .then().statusCode(200)
            .body("find { it.code == 'NEEDS_MIGRATION' }.active", is(true))
            .body("find { it.code == 'NEEDS_MIGRATION' }.readVisible", is(true));
    }

    @Test
    void theLevelDefaultIsFlaggedExactlyOnce() {
        for (String level : new String[] {"project", "issue", "tasklist", "task"}) {
            given().when().get("/api/statuses/" + level)
                .then().statusCode(200)
                .body("count { it.defaultForLevel == true }", is(1));
        }
    }

    @Test
    void theDefaultIsTheLowestPrecedenceStatusNotTheMigrationFallback() {
        given().when().get("/api/statuses/project")
            .then().statusCode(200)
            .body("find { it.defaultForLevel == true }.code", is("OPENED"));
        given().when().get("/api/statuses/task")
            .then().statusCode(200)
            .body("find { it.defaultForLevel == true }.code", is("TODO"));
    }

    @Test
    void everyLevelIsReadable() {
        given().when().get("/api/statuses/project").then().statusCode(200).body("size()", is(3));
        given().when().get("/api/statuses/issue").then().statusCode(200).body("size()", is(5));
        given().when().get("/api/statuses/tasklist").then().statusCode(200).body("size()", is(5));
        given().when().get("/api/statuses/task").then().statusCode(200).body("size()", is(6));
    }

    @Test
    void anInvalidLevelIsA400NamingTheValidSet() {
        given().when().get("/api/statuses/milestone")
            .then().statusCode(400)
            .body("error", is("INVALID_STATUS_LEVEL"))
            .body("message", containsString("tasklist"));
    }

    // Authorization is NOT asserted here: the default profile has auth disabled, so any 401/403
    // assertion would pass for the wrong reason (handoff §7). StatusDiscoveryAuthTest owns both
    // outcomes under an auth-enabled profile — that is where the deliberate "not admin-only"
    // asymmetry is actually proven.
}
