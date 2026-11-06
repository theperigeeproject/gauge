package pl.perigee.gauge.resource;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.*;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.*;

@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class GaugePageResourceTest {

    static String projectId;
    static String issueId;
    static String tasklistId;

    @Test @Order(1)
    void setupProject() {
        projectId = given().contentType(ContentType.JSON)
            .body(Map.of("name", "PageTest Project"))
            .when().post("/api/projects").then().statusCode(201).extract().path("id");
    }

    @Test @Order(2)
    void setupIssue() {
        issueId = given().contentType(ContentType.JSON)
            .body(Map.of("title", "PageTest Issue"))
            .when().post("/api/projects/" + projectId + "/issues").then().statusCode(201).extract().path("id");
    }

    @Test @Order(3)
    void setupTasklist() {
        tasklistId = given().contentType(ContentType.JSON)
            .body(Map.of("title", "PageTest Tasklist"))
            .when().post("/api/issues/" + issueId + "/tasklists").then().statusCode(201).extract().path("id");
    }

    @Test @Order(4)
    void login() {
        given().when().get("/login.html").then().statusCode(200).contentType(containsString("text/html"));
    }

    @Test @Order(5)
    void loginWithError() {
        given().when().get("/login.html?error=Invalid+credentials")
            .then().statusCode(200).contentType(containsString("text/html"));
    }

    @Test @Order(6)
    void loginFailed() {
        given().when().get("/login-failed.html").then().statusCode(200).contentType(containsString("text/html"));
    }

    @Test @Order(7)
    void loginFailedDeleted() {
        given().when().get("/login-failed.html?reason=deleted").then().statusCode(200);
    }

    @Test @Order(8)
    void loginFailedExpired() {
        given().when().get("/login-failed.html?reason=expired").then().statusCode(200);
    }

    @Test @Order(9)
    void loginFailedDefault() {
        given().when().get("/login-failed.html?reason=unknown").then().statusCode(200);
    }

    @Test @Order(10)
    void logout() {
        given().when().post("/logout")
            .then().statusCode(302).header("Location", containsString("login.html"));
    }

    @Test @Order(11)
    void dashboard() {
        // UI pages require auth; in test mode with security disabled → 403
        given().when().get("/").then().statusCode(403);
    }

    @Test @Order(12)
    void projects() {
        given().when().get("/ui/projects").then().statusCode(403);
    }

    @Test @Order(13)
    void createProject() {
        given().when().get("/ui/projects/new").then().statusCode(403);
    }

    @Test @Order(14)
    void projectDetail() {
        given().when().get("/ui/projects/" + projectId).then().statusCode(403);
    }

    @Test @Order(15)
    void createIssue() {
        given().when().get("/ui/projects/" + projectId + "/issues/new").then().statusCode(403);
    }

    @Test @Order(16)
    void issueDetail() {
        given().when().get("/ui/issues/" + issueId).then().statusCode(403);
    }

    @Test @Order(17)
    void createTasklist() {
        given().when().get("/ui/issues/" + issueId + "/tasklists/new").then().statusCode(403);
    }

    @Test @Order(18)
    void tasklistDetail() {
        given().when().get("/ui/tasklists/" + tasklistId).then().statusCode(403);
    }

    @Test @Order(19)
    void search() {
        given().when().get("/ui/search").then().statusCode(403);
    }

    @Test @Order(20)
    void users() {
        given().when().get("/ui/users").then().statusCode(403);
    }

    @Test @Order(21)
    void account() {
        given().when().get("/ui/account").then().statusCode(403);
    }

    @Test @Order(22)
    void cleanup() {
        given().when().delete("/api/tasklists/" + tasklistId + "?cascade=true");
        given().when().delete("/api/issues/" + issueId + "?cascade=true");
        given().when().delete("/api/projects/" + projectId + "?cascade=true");
    }
}
