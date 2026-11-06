package pl.perigee.gauge.resource;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.*;

@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TaskResourceTest {

    static String projectId;
    static String issueId;
    static String tasklistId;
    static String taskId;

    @Test @Order(1)
    void setupProject() {
        projectId = given().contentType(ContentType.JSON)
            .body(Map.of("name", "TaskTest Project"))
            .when().post("/api/projects").then().statusCode(201).extract().path("id");
    }

    @Test @Order(2)
    void setupIssue() {
        issueId = given().contentType(ContentType.JSON)
            .body(Map.of("title", "TaskTest Issue"))
            .when().post("/api/projects/" + projectId + "/issues").then().statusCode(201).extract().path("id");
    }

    @Test @Order(3)
    void setupTasklist() {
        tasklistId = given().contentType(ContentType.JSON)
            .body(Map.of("title", "TaskTest Tasklist"))
            .when().post("/api/issues/" + issueId + "/tasklists").then().statusCode(201).extract().path("id");
    }

    @Test @Order(4)
    void create() {
        taskId = given().contentType(ContentType.JSON)
            .body(Map.of("title", "Test Task", "description", "Task desc"))
            .when().post("/api/tasklists/" + tasklistId + "/tasks")
            .then().statusCode(201).body("id", notNullValue()).body("title", is("Test Task"))
            .extract().path("id");
    }

    @Test @Order(5)
    void listByTasklist() {
        given().when().get("/api/tasklists/" + tasklistId + "/tasks")
            .then().statusCode(200).body("size()", not(0));
    }

    @Test @Order(6)
    void reorder() {
        String t2 = given().contentType(ContentType.JSON).body(Map.of("title", "Task 2"))
            .when().post("/api/tasklists/" + tasklistId + "/tasks")
            .then().statusCode(201).extract().path("id");

        given().contentType(ContentType.JSON).body(Map.of("taskIds", List.of(t2, taskId)))
            .when().patch("/api/tasklists/" + tasklistId + "/task-order")
            .then().statusCode(200).body("size()", not(0));
    }

    @Test @Order(7)
    void listAll() {
        given().when().get("/api/tasks").then().statusCode(200).body("size()", not(0));
    }

    @Test @Order(8)
    void search() {
        given().when().get("/api/tasks?q=Test").then().statusCode(200).body("size()", not(0));
    }

    @Test @Order(9)
    void get() {
        given().when().get("/api/tasks/" + taskId).then().statusCode(200).body("title", is("Test Task"));
    }

    @Test @Order(10)
    void getNotFound() {
        given().when().get("/api/tasks/NONEXISTENT").then().statusCode(404);
    }

    @Test @Order(11)
    void patch() {
        given().contentType(ContentType.JSON)
            .body(Map.of("title", "Patched Task", "description", "Patched desc"))
            .when().patch("/api/tasks/" + taskId)
            .then().statusCode(200).body("title", is("Patched Task"));
    }

    @Test @Order(12)
    void patchNotFound() {
        given().contentType(ContentType.JSON).body(Map.of("title", "X"))
            .when().patch("/api/tasks/NONEXISTENT").then().statusCode(404);
    }

    @Test @Order(13)
    void updateStatus() {
        given().contentType(ContentType.JSON).body(Map.of("status", "DOING"))
            .when().patch("/api/tasks/" + taskId + "/status")
            .then().statusCode(200).body("status", is("DOING"));
    }

    @Test @Order(14)
    void updateStatusNotFound() {
        given().contentType(ContentType.JSON).body(Map.of("status", "DOING"))
            .when().patch("/api/tasks/NONEXISTENT/status").then().statusCode(404);
    }

    @Test @Order(15)
    void delete() {
        given().when().delete("/api/tasks/" + taskId).then().statusCode(204);
    }

    @Test @Order(16)
    void deleteNotFound() {
        given().when().delete("/api/tasks/NONEXISTENT").then().statusCode(404);
    }

    @Test @Order(17)
    void cleanup() {
        given().when().delete("/api/tasklists/" + tasklistId + "?cascade=true");
        given().when().delete("/api/issues/" + issueId + "?cascade=true");
        given().when().delete("/api/projects/" + projectId + "?cascade=true");
    }
}
