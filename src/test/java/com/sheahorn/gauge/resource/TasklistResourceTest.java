package pl.perigee.gauge.resource;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.*;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.*;

@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TasklistResourceTest {

    static String projectId;
    static String issueId;
    static String tasklistAId;
    static String tasklistBId;
    static String taskInAId;

    @Test @Order(1)
    void setupProject() {
        projectId = given().contentType(ContentType.JSON)
            .body(Map.of("name", "TLTest Project"))
            .when().post("/api/projects").then().statusCode(201).extract().path("id");
    }

    @Test @Order(2)
    void setupIssue() {
        issueId = given().contentType(ContentType.JSON)
            .body(Map.of("title", "TLTest Issue"))
            .when().post("/api/projects/" + projectId + "/issues").then().statusCode(201).extract().path("id");
    }

    @Test @Order(3)
    void createTasklistA() {
        tasklistAId = given().contentType(ContentType.JSON)
            .body(Map.of("title", "Tasklist A"))
            .when().post("/api/issues/" + issueId + "/tasklists")
            .then().statusCode(201).body("id", notNullValue()).body("title", is("Tasklist A"))
            .extract().path("id");
    }

    @Test @Order(4)
    void createTaskInA() {
        taskInAId = given().contentType(ContentType.JSON)
            .body(Map.of("title", "Task in A"))
            .when().post("/api/tasklists/" + tasklistAId + "/tasks")
            .then().statusCode(201).body("id", notNullValue())
            .extract().path("id");
    }

    @Test @Order(5)
    void createTasklistB() {
        tasklistBId = given().contentType(ContentType.JSON)
            .body(Map.of("title", "Tasklist B"))
            .when().post("/api/issues/" + issueId + "/tasklists")
            .then().statusCode(201).body("id", notNullValue())
            .extract().path("id");
    }

    @Test @Order(6)
    void listByIssue() {
        given().when().get("/api/issues/" + issueId + "/tasklists")
            .then().statusCode(200).body("size()", is(2));
    }

    @Test @Order(7)
    void listAll() {
        given().when().get("/api/tasklists").then().statusCode(200).body("size()", not(0));
    }

    @Test @Order(8)
    void search() {
        given().when().get("/api/tasklists?q=Tasklist").then().statusCode(200).body("size()", not(0));
    }

    @Test @Order(9)
    void get() {
        given().when().get("/api/tasklists/" + tasklistAId)
            .then().statusCode(200).body("title", is("Tasklist A"));
    }

    @Test @Order(10)
    void getNotFound() {
        given().when().get("/api/tasklists/NONEXISTENT").then().statusCode(404);
    }

    @Test @Order(11)
    void patch() {
        given().contentType(ContentType.JSON).body(Map.of("title", "Patched Tasklist"))
            .when().patch("/api/tasklists/" + tasklistAId)
            .then().statusCode(200).body("title", is("Patched Tasklist"));
    }

    @Test @Order(12)
    void patchNotFound() {
        given().contentType(ContentType.JSON).body(Map.of("title", "X"))
            .when().patch("/api/tasklists/NONEXISTENT").then().statusCode(404);
    }

    @Test @Order(13)
    void updateStatus() {
        given().contentType(ContentType.JSON).body(Map.of("status", "DOING"))
            .when().patch("/api/tasklists/" + tasklistAId + "/status")
            .then().statusCode(200).body("status", is("DOING"));
    }

    @Test @Order(14)
    void updateStatusNotFound() {
        given().contentType(ContentType.JSON).body(Map.of("status", "DOING"))
            .when().patch("/api/tasklists/NONEXISTENT/status").then().statusCode(404);
    }

    @Test @Order(15)
    void linkDecomposedTaskValid() {
        // Tasklist B decomposes task in A (different tasklist, same issue)
        given().contentType(ContentType.JSON).body(Map.of("taskId", taskInAId))
            .when().patch("/api/tasklists/" + tasklistBId + "/decomposes-task")
            .then().statusCode(200).body("decomposesTaskId", is(taskInAId));
    }

    @Test @Order(16)
    void linkDecomposedTaskUnset() {
        var unlinkBody = new java.util.HashMap<String, String>();
        unlinkBody.put("taskId", null);
        given().contentType(ContentType.JSON).body(unlinkBody)
            .when().patch("/api/tasklists/" + tasklistBId + "/decomposes-task")
            .then().statusCode(200).body("decomposesTaskId", nullValue());
    }

    @Test @Order(17)
    void linkDecomposedTaskSameTasklist() {
        // Tasklist A decomposing its own task — should fail
        given().contentType(ContentType.JSON).body(Map.of("taskId", taskInAId))
            .when().patch("/api/tasklists/" + tasklistAId + "/decomposes-task")
            .then().statusCode(500); // IllegalArgumentException → 500
    }

    @Test @Order(18)
    void linkDecomposedTaskNotFound() {
        given().contentType(ContentType.JSON).body(Map.of("taskId", "NONEXISTENT"))
            .when().patch("/api/tasklists/NONEXISTENT/decomposes-task").then().statusCode(404);
    }

    @Test @Order(19)
    void deleteWithoutCascade() {
        given().when().delete("/api/tasklists/" + tasklistAId)
            .then().statusCode(409).body("error", is("CASCADE_REQUIRED"));
    }

    @Test @Order(20)
    void deleteCascade() {
        given().when().delete("/api/tasklists/" + tasklistAId + "?cascade=true").then().statusCode(204);
    }

    @Test @Order(21)
    void deleteCascadeNotFound() {
        given().when().delete("/api/tasklists/NONEXISTENT?cascade=true").then().statusCode(404);
    }

    @Test @Order(22)
    void cleanup() {
        given().when().delete("/api/issues/" + issueId + "?cascade=true");
        given().when().delete("/api/projects/" + projectId + "?cascade=true");
    }
}
