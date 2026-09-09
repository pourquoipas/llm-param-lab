package com.example.llmlab;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

/**
 * End-to-end REST tests for the run + results API ({@code /api/suites/{id}/run},
 * {@code /api/run-all}, {@code /api/results}, {@code /api/results/summary}).
 *
 * <p>These run offline (no LLM): they exercise the error paths (no active model,
 * suite not found) and the read endpoints against the in-memory H2 test DB.
 * A real run is verified in Step 15 when a model is available.
 */
@QuarkusTest
class ResultResourceTest {

    static final AtomicInteger SEQ = new AtomicInteger();

    private static String uniqueName() {
        return "rest-result-suite-" + SEQ.incrementAndGet();
    }

    /** Minimal valid suite body (CONTAINS mode) so a suite exists to run against. */
    private static Map<String, Object> validBody(String name) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name);
        body.put("description", "desc");
        body.put("expectedOutput", "hello");
        body.put("expectedOutputMode", "CONTAINS");
        body.put("judgeModelId", null);
        body.put("judgePrompt", null);
        body.put("testCases", List.of(
                Map.of("name", "case1", "systemPrompt", "sys", "userPrompt", "u1", "sortOrder", 0)));
        body.put("paramSweeps", List.of(
                Map.of("paramName", "temperature", "values", "[0.3]")));
        return body;
    }

    private static Long create(Map<String, Object> body) {
        Response response = given()
                .contentType(ContentType.JSON).body(body)
                .when().post("/api/suites");
        response.then().statusCode(201);
        return response.jsonPath().getLong("id");
    }

    private static void delete(Long id) {
        given().when().delete("/api/suites/" + id).then().statusCode(204);
    }

    @Test
    void runSuiteNotFoundReturns404() {
        given().when().post("/api/suites/999999/run").then().statusCode(404);
    }

    @Test
    void runSuiteNoActiveModelReturns400() {
        Long id = create(validBody(uniqueName()));
        try {
            given().when().post("/api/suites/" + id + "/run").then().statusCode(400);
        } finally {
            delete(id);
        }
    }

    @Test
    void runAllNoActiveModelReturns400() {
        Long id = create(validBody(uniqueName()));
        try {
            given().when().post("/api/run-all").then().statusCode(400);
        } finally {
            delete(id);
        }
    }

    @Test
    void resultsForUnknownSuiteReturnsEmpty() {
        given().queryParam("suiteId", 999999)
                .when().get("/api/results")
                .then().statusCode(200)
                .body(equalTo("[]"));
    }

    @Test
    void summaryNotFoundReturns404() {
        given().queryParam("suiteId", 999999)
                .when().get("/api/results/summary")
                .then().statusCode(404);
    }

    @Test
    void summaryValidSuiteNoResultsReturnsEmpty() {
        Long id = create(validBody(uniqueName()));
        try {
            Response response = given().queryParam("suiteId", id)
                    .when().get("/api/results/summary");
            response.then().statusCode(200).body("testCases", equalTo(List.of()));
            org.junit.jupiter.api.Assertions.assertEquals(id, response.jsonPath().getLong("suiteId"));
        } finally {
            delete(id);
        }
    }
}
