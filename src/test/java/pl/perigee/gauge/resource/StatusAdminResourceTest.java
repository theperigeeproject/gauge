package pl.perigee.gauge.resource;

import pl.perigee.gauge.domain.User;
import pl.perigee.gauge.service.ApiKeyService;
import pl.perigee.gauge.service.UserService;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.*;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.*;

/**
 * {@code /api/admin/statuses/{level}} with authentication ENABLED — the only profile in which the
 * admin guard discriminates. Under the default (no-auth) profile there is no identity at all, so a
 * 403 there proves nothing (ADR 0019).
 *
 * <p>The guard fails <b>open</b> if it is ever forgotten, so every endpoint is exercised from both
 * sides: a real non-admin key must be refused, and the master key must reach the actual behaviour.
 *
 * <p>Deliberately asserts that no status <b>id</b> appears in any response. ADR 0020 §2 makes ids
 * internal, and a leaked id is the kind of thing that quietly becomes a wire contract.
 */
@QuarkusTest
@TestProfile(StatusAdminResourceTest.Profile.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class StatusAdminResourceTest {

    public static class Profile implements io.quarkus.test.junit.QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                "gauge.auth.enabled", "true",
                "gauge.auth.seed-on-startup", "true",
                "gauge.auth.admin.username", "admin",
                "gauge.auth.admin.password", "admin",
                "gauge.api-key.access", "test-master-key",
                "gauge.api-key.pepper", "test-pepper"
            );
        }
    }

    private static final String MASTER = "test-master-key";
    private static final String BASE = "/api/admin/statuses";

    @Inject UserService userService;
    @Inject ApiKeyService apiKeyService;

    static String nonAdminKey;

    // ── Setup ───────────────────────────────────────────────────────

    @Test @Order(1)
    void setupNonAdminKey() {
        QuarkusTransaction.run(() -> {
            User user = userService.create("status-admin-plain", "secret", "user");
            nonAdminKey = apiKeyService.create(user.id, "status-admin-plain-key", null, null).rawKey;
        });
        Assertions.assertNotNull(nonAdminKey);
    }

    // ── Authorization: both outcomes, every verb ────────────────────

    @Test @Order(10)
    void noAuthorizationHeaderIsRejectedOnEveryVerb() {
        given().contentType(ContentType.JSON).when().get(BASE + "/issue").then().statusCode(401);
        given().contentType(ContentType.JSON).body("{}").when().post(BASE + "/issue").then().statusCode(401);
        given().contentType(ContentType.JSON).body("{}").when().patch(BASE + "/issue/TODO").then().statusCode(401);
        given().contentType(ContentType.JSON).when().delete(BASE + "/issue/TODO").then().statusCode(401);
    }

    @Test @Order(11)
    void nonAdminKeyIsForbiddenOnEveryVerb() {
        // Malformed bodies on purpose: proves the role check runs BEFORE validation, so an
        // unauthorized caller never learns anything about the request's shape (ADR 0019).
        given().header("Authorization", "Bearer " + nonAdminKey)
            .contentType(ContentType.JSON).when().get(BASE + "/issue")
            .then().statusCode(403).body("error", is("FORBIDDEN"));

        given().header("Authorization", "Bearer " + nonAdminKey)
            .contentType(ContentType.JSON).body("{}").when().post(BASE + "/issue")
            .then().statusCode(403).body("error", is("FORBIDDEN"));

        given().header("Authorization", "Bearer " + nonAdminKey)
            .contentType(ContentType.JSON).body("{}").when().patch(BASE + "/issue/TODO")
            .then().statusCode(403).body("error", is("FORBIDDEN"));

        given().header("Authorization", "Bearer " + nonAdminKey)
            .contentType(ContentType.JSON).when().delete(BASE + "/issue/TODO?policy=remove")
            .then().statusCode(403).body("error", is("FORBIDDEN"));
    }

    // ── Read ────────────────────────────────────────────────────────

    @Test @Order(20)
    void listReturnsTheSeededStatusesOrderedByPrecedence() {
        given().header("Authorization", "Bearer " + MASTER)
            .when().get(BASE + "/issue")
            .then().statusCode(200)
            .body("size()", is(5))
            .body("[0].code", is("TODO"))
            .body("[1].code", is("DOING"))
            .body("[2].code", is("DONE"))
            .body("[3].code", is("INACTIVE"))
            .body("[4].code", is("NEEDS_MIGRATION"))
            .body("[0].attitudeType", is("REGULAR"))
            .body("[0].progressLevel", is("CREATED"));
    }

    @Test @Order(21)
    void noStatusIdLeaksIntoTheResponse() {
        String body = given().header("Authorization", "Bearer " + MASTER)
            .when().get(BASE + "/issue")
            .then().statusCode(200)
            .extract().asString();

        Assertions.assertFalse(body.contains("\"id\""),
            "ADR 0020 §2: a status id is internal and must never reach a caller. Body: " + body);
    }

    @Test @Order(22)
    void anInvalidLevelIsA400NamingTheValidSet() {
        given().header("Authorization", "Bearer " + MASTER)
            .when().get(BASE + "/milestone")
            .then().statusCode(400)
            .body("error", is("INVALID_STATUS_LEVEL"))
            .body("message", containsString("tasklist"));
    }

    // ── Create ──────────────────────────────────────────────────────

    @Test @Order(30)
    void createThenFindItInTheList() {
        given().header("Authorization", "Bearer " + MASTER)
            .contentType(ContentType.JSON)
            .body(Map.of("code", "REVIEWING", "precedenceValue", 9, "color", "#123456",
                "progressLevel", "ONGOING", "attitudeType", "REGULAR"))
            .when().post(BASE + "/issue")
            .then().statusCode(201)
            .body("code", is("REVIEWING"))
            .body("precedenceValue", is(9))
            .body("progressLevel", is("ONGOING"))
            .body("attitudeType", is("REGULAR"));

        given().header("Authorization", "Bearer " + MASTER)
            .when().get(BASE + "/issue")
            .then().statusCode(200)
            .body("code", hasItem("REVIEWING"));
    }

    @Test @Order(31)
    void aDuplicateCodeIsA409NotASilentMerge() {
        given().header("Authorization", "Bearer " + MASTER)
            .contentType(ContentType.JSON)
            .body(Map.of("code", "DOING", "precedenceValue", 42, "color", "#ffffff",
                "progressLevel", "ONGOING", "attitudeType", "REGULAR"))
            .when().post(BASE + "/issue")
            .then().statusCode(409)
            .body("error", is("DUPLICATE_STATUS_CODE"));
    }

    @Test @Order(32)
    void aFlagDisabledAttitudeIsA400NamingTheProperty() {
        // gauge.status.trash-enabled defaults false.
        given().header("Authorization", "Bearer " + MASTER)
            .contentType(ContentType.JSON)
            .body(Map.of("code", "TRASHED_X", "precedenceValue", 40, "color", "#000000",
                "progressLevel", "FINISHED", "attitudeType", "TRASH"))
            .when().post(BASE + "/issue")
            .then().statusCode(400)
            .body("error", is("ATTITUDE_DISABLED"))
            .body("message", containsString("gauge.status.trash-enabled"));
    }

    @Test @Order(33)
    void aSecondMigrationStatusIsRefused() {
        given().header("Authorization", "Bearer " + MASTER)
            .contentType(ContentType.JSON)
            .body(Map.of("code", "MIGRATION2", "precedenceValue", 41, "color", "#ffffff",
                "progressLevel", "HALTED", "attitudeType", "MIGRATION"))
            .when().post(BASE + "/issue")
            .then().statusCode(409)
            .body("error", is("DUPLICATE_MIGRATION_STATUS"));
    }

    // ── Update ──────────────────────────────────────────────────────

    @Test @Order(40)
    void updateRenamesAndChangesTheAxes() {
        given().header("Authorization", "Bearer " + MASTER)
            .contentType(ContentType.JSON)
            .body(Map.of("code", "IN_REVIEW", "precedenceValue", 9, "color", "#abcdef",
                "progressLevel", "ONGOING", "attitudeType", "REGULAR"))
            .when().patch(BASE + "/issue/REVIEWING")
            .then().statusCode(200)
            .body("code", is("IN_REVIEW"))
            .body("color", is("#abcdef"));

        given().header("Authorization", "Bearer " + MASTER)
            .when().get(BASE + "/issue")
            .then().statusCode(200)
            .body("code", hasItem("IN_REVIEW"))
            .body("code", not(hasItem("REVIEWING")));
    }

    @Test @Order(41)
    void movingTheMigrationStatusToAnotherAttitudeIsRefused() {
        given().header("Authorization", "Bearer " + MASTER)
            .contentType(ContentType.JSON)
            .body(Map.of("code", "NEEDS_MIGRATION", "precedenceValue", 4, "color", "#fb923c",
                "progressLevel", "HALTED", "attitudeType", "REGULAR"))
            .when().patch(BASE + "/issue/NEEDS_MIGRATION")
            .then().statusCode(409)
            .body("error", is("MIGRATION_STATUS_PROTECTED"));
    }

    @Test @Order(42)
    void updatingAnUnknownStatusIsA400() {
        given().header("Authorization", "Bearer " + MASTER)
            .contentType(ContentType.JSON)
            .body(Map.of("code", "GHOST", "precedenceValue", 50, "color", "#ffffff",
                "progressLevel", "ONGOING", "attitudeType", "REGULAR"))
            .when().patch(BASE + "/issue/NOPE")
            .then().statusCode(400)
            .body("error", is("UNKNOWN_STATUS"));
    }

    // ── Delete ──────────────────────────────────────────────────────

    @Test @Order(50)
    void deletingAnUnusedStatusNeedsNoPolicy() {
        given().header("Authorization", "Bearer " + MASTER)
            .when().delete(BASE + "/issue/IN_REVIEW")
            .then().statusCode(204);

        given().header("Authorization", "Bearer " + MASTER)
            .when().get(BASE + "/issue")
            .then().statusCode(200)
            .body("code", not(hasItem("IN_REVIEW")));
    }

    @Test @Order(51)
    void theMigrationStatusCannotBeDeletedUnderAnyPolicy() {
        for (String policy : new String[]{"", "?policy=prevent", "?policy=remove",
            "?policy=migrate&migrateTo=TODO"}) {
            given().header("Authorization", "Bearer " + MASTER)
                .when().delete(BASE + "/issue/NEEDS_MIGRATION" + policy)
                .then().statusCode(409)
                .body("error", is("MIGRATION_STATUS_PROTECTED"));
        }
    }

    @Test @Order(52)
    void anInvalidRemovalPolicyIsA400NamingTheValidSet() {
        given().header("Authorization", "Bearer " + MASTER)
            .when().delete(BASE + "/issue/TODO?policy=ignore")
            .then().statusCode(400)
            .body("error", is("INVALID_REMOVAL_POLICY"))
            .body("message", containsString("migrate"));
    }

    @Test @Order(53)
    void theLastAvailableStatusCannotBeRemoved() {
        // Project level: OPENED, CLOSED, NEEDS_MIGRATION. The fallback must not count toward the
        // invariant, so removing the two real ones trips it.
        given().header("Authorization", "Bearer " + MASTER)
            .when().delete(BASE + "/project/CLOSED")
            .then().statusCode(204);

        given().header("Authorization", "Bearer " + MASTER)
            .when().delete(BASE + "/project/OPENED")
            .then().statusCode(409)
            .body("error", is("LAST_STATUS"));
    }
}
