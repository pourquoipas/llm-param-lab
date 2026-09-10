package dev.gnius.llmlab;

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
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.nullValue;

/**
 * End-to-end REST tests for {@code /api/suites} against the in-memory H2 test DB.
 * Each test uses a unique suite name so the shared test database stays independent.
 */
@QuarkusTest
class TestSuiteResourceTest {

    static final AtomicInteger SEQ = new AtomicInteger();

    private static String uniqueName() {
        return "rest-suite-" + SEQ.incrementAndGet();
    }

    /** A test-case map that tolerates a null systemPrompt (Map.of does not). */
    private static Map<String, Object> caseMap(String name, String systemPrompt, String userPrompt, int sortOrder) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name);
        m.put("systemPrompt", systemPrompt);
        m.put("userPrompt", userPrompt);
        m.put("sortOrder", sortOrder);
        return m;
    }

    /** A valid create body: CONTAINS mode with expectedOutput, 2 cases, 2 sweeps. */
    private static Map<String, Object> validBody(String name) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name);
        body.put("description", "desc");
        body.put("expectedOutput", "hello");
        body.put("expectedOutputMode", "CONTAINS");
        body.put("judgeModelId", null);
        body.put("judgePrompt", null);
        body.put("testCases", List.of(
                caseMap("case1", "sys", "user1", 0),
                caseMap("case2", null, "user2", 1)));
        body.put("paramSweeps", List.of(
                Map.of("paramName", "temperature", "values", "[0.3, 0.5]"),
                Map.of("paramName", "topP", "values", "[0.9]")));
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
    void createRoundTripReturnsIdenticalData() {
        String name = uniqueName();
        Long id = create(validBody(name));
        try {
            given().when().get("/api/suites/" + id)
                    .then().statusCode(200)
                    .body("name", equalTo(name))
                    .body("expectedOutputMode", equalTo("CONTAINS"))
                    .body("expectedOutput", equalTo("hello"))
                    .body("testCases.size()", equalTo(2))
                    .body("paramSweeps.size()", equalTo(2))
                    .body("testCases[0].name", equalTo("case1"))
                    .body("testCases[0].systemPrompt", equalTo("sys"))
                    .body("testCases[1].systemPrompt", nullValue())
                    .body("paramSweeps[0].paramName", equalTo("temperature"))
                    .body("paramSweeps[0].values", equalTo("[0.3, 0.5]"))
                    .body("paramSweeps[1].paramName", equalTo("topP"));
        } finally {
            delete(id);
        }
    }

    @Test
    void createWithExtendedChatParamsSucceeds() {
        String name = uniqueName();
        Map<String, Object> body = validBody(name);
        body.put("paramSweeps", List.of(
                Map.of("paramName", "topK", "values", "[40, 80]"),
                Map.of("paramName", "frequencyPenalty", "values", "[0.0, 0.5]"),
                Map.of("paramName", "presencePenalty", "values", "[0.25]"),
                Map.of("paramName", "maxTokens", "values", "[256]")));
        Long id = create(body);
        try {
            given().when().get("/api/suites/" + id)
                    .then().statusCode(200)
                    .body("paramSweeps.size()", equalTo(4))
                    .body("paramSweeps[0].paramName", equalTo("topK"));
        } finally {
            delete(id);
        }
    }

    @Test
    void createAppearsInList() {
        String name = uniqueName();
        Long id = create(validBody(name));
        try {
            given().when().get("/api/suites")
                    .then().statusCode(200)
                    .body("name", hasItem(name));
        } finally {
            delete(id);
        }
    }

    @Test
    void createWithMissingNameReturns400() {
        Map<String, Object> body = validBody(null);
        body.remove("name");
        given().contentType(ContentType.JSON).body(body)
                .when().post("/api/suites")
                .then().statusCode(400);
    }

    @Test
    void createWithModeButNoExpectedOutputReturns400() {
        Map<String, Object> body = validBody(uniqueName());
        body.put("expectedOutput", null);
        given().contentType(ContentType.JSON).body(body)
                .when().post("/api/suites")
                .then().statusCode(400);
    }

    @Test
    void createWithNoneModeButNoJudgeReturns400() {
        Map<String, Object> body = validBody(uniqueName());
        body.put("expectedOutputMode", "NONE");
        body.put("expectedOutput", null);
        body.put("judgeModelId", null);
        given().contentType(ContentType.JSON).body(body)
                .when().post("/api/suites")
                .then().statusCode(400);
    }

    @Test
    void createWithBadParamNameReturns400() {
        Map<String, Object> body = validBody(uniqueName());
        body.put("paramSweeps", List.of(Map.of("paramName", "foo", "values", "[1]")));
        given().contentType(ContentType.JSON).body(body)
                .when().post("/api/suites")
                .then().statusCode(400);
    }

    @Test
    void createWithBadValuesReturns400() {
        Map<String, Object> body = validBody(uniqueName());
        body.put("paramSweeps", List.of(Map.of("paramName", "temperature", "values", "not-json")));
        given().contentType(ContentType.JSON).body(body)
                .when().post("/api/suites")
                .then().statusCode(400);
    }

    @Test
    void getNotFoundReturns404() {
        given().when().get("/api/suites/999999").then().statusCode(404);
    }

    @Test
    void updateChangesFields() {
        String name = uniqueName();
        Long id = create(validBody(name));
        try {
            Map<String, Object> updated = validBody(name);
            updated.put("description", "updated");
            updated.put("expectedOutput", "world");
            given().contentType(ContentType.JSON).body(updated)
                    .when().put("/api/suites/" + id)
                    .then().statusCode(200)
                    .body("description", equalTo("updated"))
                    .body("expectedOutput", equalTo("world"))
                    .body("testCases.size()", equalTo(2));
        } finally {
            delete(id);
        }
    }

    @Test
    void updateNotFoundReturns404() {
        given().contentType(ContentType.JSON).body(validBody(uniqueName()))
                .when().put("/api/suites/999999")
                .then().statusCode(404);
    }

    @Test
    void deleteRemovesSuite() {
        String name = uniqueName();
        Long id = create(validBody(name));
        delete(id);
        given().when().get("/api/suites/" + id).then().statusCode(404);
    }

    @Test
    void deleteNotFoundReturns404() {
        given().when().delete("/api/suites/999999").then().statusCode(404);
    }

    @Test
    void judgeParamsRoundTrip() {
        // I3: savable judge params round-trip through the API.
        Map<String, Object> body = validBody(uniqueName());
        body.put("judgeTemperature", 0.2);
        body.put("judgeTopP", 0.9);
        body.put("judgeSeed", 42);
        Long id = create(body);
        try {
            given().when().get("/api/suites/" + id)
                    .then().statusCode(200)
                    .body("judgeTemperature", equalTo(0.2f))
                    .body("judgeTopP", equalTo(0.9f))
                    .body("judgeSeed", equalTo(42));
        } finally {
            delete(id);
        }
    }

    @Test
    void seedsRoundTrip() {
        // I4a: the seed-sweep list round-trips through the API.
        Map<String, Object> body = validBody(uniqueName());
        body.put("seeds", java.util.List.of(7, 42));
        Long id = create(body);
        try {
            given().when().get("/api/suites/" + id)
                    .then().statusCode(200)
                    .body("seeds", equalTo(java.util.List.of(7, 42)));
        } finally {
            delete(id);
        }
    }
}
