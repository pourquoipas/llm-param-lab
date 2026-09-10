package dev.gnius.llmlab;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;

/**
 * Verifies the startup seed data: the default model (from .env) and the "JSON extraction
 * test" suite (3 test cases + 2 sweeps, judge → default model). The seed runs once at app
 * startup against the in-memory H2 test DB, so the data is present for these assertions.
 */
@QuarkusTest
class SeedDataTest {

    @Test
    void seedsDefaultModel() {
        // Other test classes add models to the shared in-memory DB, so assert the default
        // model is present with the right config rather than the total count.
        given().when().get("/api/models")
                .then().statusCode(200)
                .body("name", hasItem("default"))
                .body("baseUrl", hasItem("http://localhost:11000/v1"))
                .body("modelName", hasItem("Qwen3.8-27B-UD-IQ3_S.gguf"));
    }

    @Test
    void seedsJsonExtractionSuite() {
        Integer defaultModelId = idByName("/api/models", "default");
        Integer suiteId = idByName("/api/suites", "JSON extraction test");

        given().when().get("/api/suites/" + suiteId)
                .then().statusCode(200)
                .body("name", equalTo("JSON extraction test"))
                .body("expectedOutputMode", equalTo("NONE"))
                .body("judgeModelId", equalTo(defaultModelId))
                .body("testCases.size()", equalTo(3))
                .body("paramSweeps.size()", equalTo(2))
                .body("paramSweeps[0].paramName", equalTo("temperature"))
                .body("paramSweeps[0].values", equalTo("[0.0, 0.3, 0.7]"))
                .body("paramSweeps[1].paramName", equalTo("topP"))
                .body("paramSweeps[1].values", equalTo("[0.9, 0.95]"));
    }

    /** Returns the id of the item whose "name" field equals {@code name} in a list endpoint. */
    private static Integer idByName(String path, String name) {
        Response resp = given().when().get(path);
        resp.then().statusCode(200);
        var ids = resp.jsonPath().getList("id", Integer.class);
        var names = resp.jsonPath().getList("name", String.class);
        for (int i = 0; i < names.size(); i++) {
            if (name.equals(names.get(i))) {
                return ids.get(i);
            }
        }
        throw new AssertionError("Not found: " + name);
    }
}
