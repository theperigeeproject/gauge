package pl.perigee.gauge.resource;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.*;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.*;

@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class IssueResourceTest {

    static String projectId;
    static String childProjectId;
    static String issueId;
    static String tasklistId;

    @Test @Order(1)
    void setupProject() {
        projectId = given().contentType(ContentType.JSON)
            .body(Map.of("name", "IssueTest Project"))
            .when().post("/api/projects").then().statusCode(201).extract().path("id");
        childProjectId = given().contentType(ContentType.JSON)
            .body(Map.of("name", "IssueTest Child", "parentId", projectId))
            .when().post("/api/projects").then().statusCode(201).extract().path("id");
    }

    @Test @Order(2)
    void create() {
        issueId = given().contentType(ContentType.JSON)
            .body(Map.of("title", "Test Issue", "description", "Issue desc", "priority", "HIGH"))
            .when().post("/api/projects/" + projectId + "/issues")
            .then().statusCode(201).body("id", notNullValue()).body("title", is("Test Issue"))
            .body("priority", is("HIGH")).extract().path("id");
    }

    @Test @Order(3)
    void createDefaultPriority() {
        given().contentType(ContentType.JSON).body(Map.of("title", "Default Prio Issue"))
            .when().post("/api/projects/" + projectId + "/issues")
            .then().statusCode(201).body("priority", is("NORMAL"));
    }

    @Test @Order(4)
    void listByProject() {
        given().when().get("/api/projects/" + projectId + "/issues")
            .then().statusCode(200).body("size()", not(0));
    }

    @Test @Order(5)
    void listByProjectWithSort() {
        given().when().get("/api/projects/" + projectId + "/issues?sort=NAME").then().statusCode(200);
    }

    @Test @Order(6)
    void listByProjectInvalidSort() {
        given().when().get("/api/projects/" + projectId + "/issues?sort=INVALID").then().statusCode(200);
    }

    @Test @Order(7)
    void listByProjectWithPriorityFilter() {
        given().when().get("/api/projects/" + projectId + "/issues?priority=HIGH").then().statusCode(200);
    }

    @Test @Order(8)
    void listByProjectWithStatusFilter() {
        given().when().get("/api/projects/" + projectId + "/issues?status=TODO").then().statusCode(200);
    }

    @Test @Order(9)
    void listByProjectInvalidPriorityIgnored() {
        given().when().get("/api/projects/" + projectId + "/issues?priority=INVALID").then().statusCode(200);
    }

    @Test @Order(10)
    void listByProjectEmptyParams() {
        given().when().get("/api/projects/" + projectId + "/issues?priority=&status=").then().statusCode(200);
    }

    @Test @Order(11)
    void listAll() {
        given().when().get("/api/issues").then().statusCode(200).body("size()", not(0));
    }

    @Test @Order(12)
    void search() {
        given().when().get("/api/issues?q=Test").then().statusCode(200).body("size()", not(0));
    }

    @Test @Order(13)
    void get() {
        given().when().get("/api/issues/" + issueId).then().statusCode(200).body("title", is("Test Issue"));
    }

    @Test @Order(14)
    void getNotFound() {
        given().when().get("/api/issues/NONEXISTENT").then().statusCode(404);
    }

    @Test @Order(15)
    void patch() {
        given().contentType(ContentType.JSON)
            .body(Map.of("title", "Patched Issue", "description", "Patched desc"))
            .when().patch("/api/issues/" + issueId)
            .then().statusCode(200).body("title", is("Patched Issue"));
    }

    @Test @Order(16)
    void patchNotFound() {
        given().contentType(ContentType.JSON).body(Map.of("title", "X"))
            .when().patch("/api/issues/NONEXISTENT").then().statusCode(404);
    }

    @Test @Order(17)
    void updateStatus() {
        given().contentType(ContentType.JSON).body(Map.of("status", "DOING"))
            .when().patch("/api/issues/" + issueId + "/status")
            .then().statusCode(200).body("status", is("DOING"));
    }

    @Test @Order(18)
    void updateStatusNotFound() {
        given().contentType(ContentType.JSON).body(Map.of("status", "DOING"))
            .when().patch("/api/issues/NONEXISTENT/status").then().statusCode(404);
    }

    @Test @Order(19)
    void updatePriority() {
        given().contentType(ContentType.JSON).body(Map.of("priority", "LOW"))
            .when().patch("/api/issues/" + issueId + "/priority")
            .then().statusCode(200).body("priority", is("LOW"));
    }

    @Test @Order(20)
    void updatePriorityNotFound() {
        given().contentType(ContentType.JSON).body(Map.of("priority", "HIGH"))
            .when().patch("/api/issues/NONEXISTENT/priority").then().statusCode(404);
    }

    @Test @Order(21)
    void moveToProject() {
        given().contentType(ContentType.JSON).body(Map.of("projectId", childProjectId))
            .when().patch("/api/issues/" + issueId + "/project")
            .then().statusCode(200).body("projectId", is(childProjectId));
    }

    @Test @Order(22)
    void moveBack() {
        given().contentType(ContentType.JSON).body(Map.of("projectId", projectId))
            .when().patch("/api/issues/" + issueId + "/project")
            .then().statusCode(200).body("projectId", is(projectId));
    }

    @Test @Order(23)
    void moveNotFound() {
        given().contentType(ContentType.JSON).body(Map.of("projectId", projectId))
            .when().patch("/api/issues/NONEXISTENT/project").then().statusCode(404);
    }

    @Test @Order(24)
    void createTasklistForCascadeTest() {
        tasklistId = given().contentType(ContentType.JSON)
            .body(Map.of("title", "Cascade Test TL"))
            .when().post("/api/issues/" + issueId + "/tasklists")
            .then().statusCode(201).extract().path("id");
    }

    @Test @Order(25)
    void deleteWithoutCascade() {
        // Now has a tasklist → 409
        given().when().delete("/api/issues/" + issueId)
            .then().statusCode(409).body("error", is("CASCADE_REQUIRED"));
    }

    @Test @Order(26)
    void deleteCascade() {
        given().when().delete("/api/issues/" + issueId + "?cascade=true").then().statusCode(204);
    }

    @Test @Order(27)
    void deleteCascadeNotFound() {
        given().when().delete("/api/issues/NONEXISTENT?cascade=true").then().statusCode(404);
    }

    @Test @Order(28)
    void cleanup() {
        given().when().delete("/api/projects/" + projectId + "?cascade=true");
    }
}
