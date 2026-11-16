package pl.perigee.gauge.resource;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.path.json.JsonPath;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Guards the published OpenAPI document's {@code operationId}s.
 *
 * <p><b>Why this exists.</b> An {@code operationId} is not a doc annotation here — it is the name of
 * an LLM tool. {@code tracker_analyze_projects}, {@code tracker_create_project_issue} and friends are
 * how this tracker is driven. Two methods sharing one id make the generated tool set ambiguous (the
 * OpenAPI scanner warns {@code SROAP07903} and the tool layer has to guess), which is a silent
 * regression: the build stays green and the damage appears in an agent's tool list.
 *
 * <p>That is exactly what happened while adding {@code GET /api/statuses/{level}} — the new
 * operationId collided with the admin resource's, and only the scanner's WARN said so.
 *
 * <p>Two properties, both cheap and both load-bearing: every id is <b>unique</b>, and every id is
 * <b>prefixed {@code tracker_}</b> so a tool name cannot drift from the family the LLM is told about.
 */
@QuarkusTest
class OpenApiOperationIdTest {

    @Test
    void operationIdsAreUniqueAndPrefixed() {
        JsonPath spec = given().when().get("/q/openapi?format=json").then()
            .statusCode(200).extract().jsonPath();

        Map<String, Map<String, Object>> paths = spec.getMap("paths");
        assertNotNull(paths, "the OpenAPI document must expose paths");

        List<String> ids = paths.values().stream()
            .flatMap(operations -> operations.values().stream())
            .filter(op -> op instanceof Map<?, ?>)
            .map(op -> ((Map<?, ?>) op).get("operationId"))
            .filter(java.util.Objects::nonNull)
            .map(Object::toString)
            .toList();

        assertFalse(ids.isEmpty(), "no operationId was published at all — the scan is broken");

        List<String> duplicates = ids.stream()
            .distinct()
            .filter(id -> ids.stream().filter(other -> other.equals(id)).count() > 1)
            .toList();
        assertTrue(duplicates.isEmpty(),
            "operationIds double as LLM tool names and must be unique. Duplicated: " + duplicates);

        List<String> unprefixed = ids.stream().filter(id -> !id.startsWith("tracker_")).toList();
        assertTrue(unprefixed.isEmpty(),
            "every tool must stay in the tracker_ family: " + unprefixed);
    }
}
