package pl.perigee.gauge.resource;

import pl.perigee.gauge.domain.Issue;
import pl.perigee.gauge.domain.Priority;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.Matchers.hasKey;

/**
 * The wire contract after increment 4 of 0000186F (ADR 0020 §2):
 *
 * <ul>
 *   <li>a status crosses the wire as a <b>code</b>, never an internal id;</li>
 *   <li>the internal status id must not appear in any response;</li>
 *   <li>codes are matched case-insensitively on the way in and echoed canonically;</li>
 *   <li>an unknown code is a 400 naming the valid set (ADR 0018), never a silent default;</li>
 *   <li>{@code ?status=NEEDS_MIGRATION} reaches the rows that actually read as that status.</li>
 * </ul>
 *
 * <p>Runs under the default profile: no auth identity, which is irrelevant here — this is about the
 * body, not the guard.
 */
@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class StatusWireShapeTest {

    static String projectId;
    static String issueId;

    @Inject
    EntityManager em;

    @Test
    @Order(1)
    void setup() {
        projectId = given().contentType(ContentType.JSON)
            .body(Map.of("name", "Wire Shape Project"))
            .when().post("/api/projects").then().statusCode(201).extract().path("id");
    }

    @Test
    @Order(2)
    void aProjectCarriesAStatusCodeAndNoStatusId() {
        given().when().get("/api/projects/" + projectId)
            .then().statusCode(200)
            .body("status", is("OPENED"))
            .body("$", not(hasKey("statusId")));
    }

    @Test
    @Order(3)
    void aNewIssueGetsTheLevelDefaultStatusAndNoIdOnTheWire() {
        issueId = given().contentType(ContentType.JSON)
            .body(Map.of("title", "Wire issue"))
            .when().post("/api/projects/" + projectId + "/issues")
            .then().statusCode(201)
            .body("status", is("TODO"))
            .body("$", not(hasKey("statusId")))
            .extract().path("id");
    }

    @Test
    @Order(4)
    void statusCodesAreCaseInsensitiveInAndCanonicalOut() {
        given().contentType(ContentType.JSON).body(Map.of("status", "doing"))
            .when().patch("/api/issues/" + issueId + "/status")
            .then().statusCode(200).body("status", is("DOING"));
    }

    @Test
    @Order(5)
    void anUnknownStatusIsA400NamingTheValidSet() {
        given().contentType(ContentType.JSON).body(Map.of("status", "MEDIUM"))
            .when().patch("/api/issues/" + issueId + "/status")
            .then().statusCode(400)
            .body("error", is("INVALID_STATUS"))
            .body("message", containsString("TODO"))
            .body("message", containsString("INACTIVE"));
    }

    @Test
    @Order(6)
    void theFilterAcceptsACodeAndMatchesByStatus() {
        given().when().get("/api/projects/" + projectId + "/issues?status=doing")
            .then().statusCode(200).body("size()", is(1));

        given().when().get("/api/projects/" + projectId + "/issues?status=TODO")
            .then().statusCode(200).body("size()", is(0));
    }

    @Test
    @Order(7)
    void anUnknownFilterCodeIsA400NotAnEmptyList() {
        given().when().get("/api/projects/" + projectId + "/issues?status=NOPE")
            .then().statusCode(400).body("error", is("INVALID_STATUS"));
    }

    @Test
    @Order(8)
    void filteringByTheMigrationStatusReachesUnmigratedRows() {
        // A row that predates the switchover: null status id, legacy code still present. It reads
        // as NEEDS_MIGRATION, and the filter has to agree with what the row renders as.
        QuarkusTransaction.run(() -> {
            Issue legacy = new Issue("WIRE0001", projectId, "Legacy row", "desc", null, Priority.NORMAL);
            legacy.status = "SOMETHING_OLD";
            em.persist(legacy);
        });

        given().when().get("/api/projects/" + projectId + "/issues?status=NEEDS_MIGRATION")
            .then().statusCode(200)
            .body("size()", is(1))
            .body("[0].status", is("NEEDS_MIGRATION"));

        // And it must not be claimed by a filter on a real status.
        given().when().get("/api/projects/" + projectId + "/issues?status=DOING")
            .then().statusCode(200).body("size()", is(1));
    }

    @Test
    @Order(9)
    void cleanup() {
        given().when().delete("/api/projects/" + projectId + "?cascade=true").then().statusCode(204);
    }
}
