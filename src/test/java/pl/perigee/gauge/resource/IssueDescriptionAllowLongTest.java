package pl.perigee.gauge.resource;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.*;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.*;

/**
 * The same contract with {@code gauge.issue.description.allow-long=true}: the cap moves to the
 * database width, the failure mode does not change.
 *
 * <p>Needs its own {@link TestProfile} because the toggle is a config property read at boot; a
 * separate profile means a separate Quarkus instance and a separate in-memory database.
 */
@QuarkusTest
@TestProfile(IssueDescriptionAllowLongTest.Profile.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class IssueDescriptionAllowLongTest {

    public static class Profile implements io.quarkus.test.junit.QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("gauge.issue.description.allow-long", "true");
        }
    }

    static final String OVER_OLD_CAP = "x".repeat(4001);
    static final String OVER_HARD_CAP = "x".repeat(1_000_001);

    static String projectId;
    static String issueId;

    @Test @Order(1)
    void setup() {
        projectId = given().contentType(ContentType.JSON)
            .body(Map.of("name", "Allow Long Project"))
            .when().post("/api/projects")
            .then().statusCode(201).extract().path("id");
    }

    @Test @Order(2)
    void createOverTheOldCapIsAccepted() {
        issueId = given().contentType(ContentType.JSON)
            .body(Map.of("title", "long", "description", OVER_OLD_CAP))
            .when().post("/api/projects/" + projectId + "/issues")
            .then().statusCode(201)
            .extract().path("id");
    }

    @Test @Order(3)
    void patchOverTheOldCapIsAccepted() {
        given().contentType(ContentType.JSON)
            .body(Map.of("description", OVER_OLD_CAP))
            .when().patch("/api/issues/" + issueId)
            .then().statusCode(200);
    }

    @Test @Order(4)
    void theHardCeilingStillRejects() {
        given().contentType(ContentType.JSON)
            .body(Map.of("title", "too long", "description", OVER_HARD_CAP))
            .when().post("/api/projects/" + projectId + "/issues")
            .then().statusCode(400)
            .body("error", is("DESCRIPTION_TOO_LONG"))
            .body("message", containsString("hard database limit"));
    }

    @Test @Order(5)
    void cleanup() {
        given().when().delete("/api/projects/" + projectId + "?cascade=true");
    }
}
