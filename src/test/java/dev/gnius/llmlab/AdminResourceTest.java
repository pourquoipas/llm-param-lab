package dev.gnius.llmlab;

import dev.gnius.llmlab.seed.SeedData;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end REST tests for the admin API ({@code /api/admin/clean},
 * {@code /api/admin/test-case}) against the in-memory H2 test DB.
 *
 * <p>These run offline (no LLM): clean only wipes rows and test-case only creates data
 * (it never runs a suite). After clean, the seed is re-run so the shared test DB keeps
 * the data other test classes rely on.
 */
@QuarkusTest
class AdminResourceTest {

    @Inject
    SeedData seedData;

    @Test
    void cleanWipesDatabase() {
        given().when().post("/api/admin/clean").then().statusCode(204);
        try {
            given().when().get("/api/models").then().statusCode(200).body(equalTo("[]"));
            given().when().get("/api/suites").then().statusCode(200).body(equalTo("[]"));
        } finally {
            seedData.seed();
        }
    }

    @Test
    void testCaseCreatesSmokeSuite() {
        Long suiteId = given().when().post("/api/admin/test-case")
                .then().statusCode(201)
                .extract().jsonPath().getLong("id");

        assertTrue(defaultModelActive(), "default model should be active after test-case");
        Integer judgeId = idByName("/api/judges", "code evaluation");

        given().when().get("/api/suites/" + suiteId)
                .then().statusCode(200)
                .body("name", equalTo("code reviewer"))
                .body("judgeId", equalTo(judgeId))
                .body("testCases.size()", equalTo(2))
                .body("testCases[0].name", equalTo("system thinker"))
                .body("testCases[1].name", equalTo("Audit protocol"))
                .body("paramSweeps.size()", equalTo(1))
                .body("paramSweeps[0].paramName", equalTo("temperature"))
                .body("paramSweeps[0].values", equalTo("[0.1, 1.0]"));

        // Idempotent: re-running replaces children instead of duplicating them.
        given().when().post("/api/admin/test-case").then().statusCode(201);
        given().when().get("/api/suites/" + suiteId)
                .then().statusCode(200)
                .body("testCases.size()", equalTo(2));

        // The code judge is name-guarded: re-running must not duplicate it.
        Response judges = given().when().get("/api/judges");
        judges.then().statusCode(200);
        long codeJudgeCount = judges.jsonPath().getList("name", String.class).stream()
                .filter("code evaluation"::equals).count();
        assertEquals(1, codeJudgeCount, "code evaluation judge should be created exactly once");
    }

    /** True if the "default" model is currently the active one. */
    private static boolean defaultModelActive() {
        Response resp = given().when().get("/api/models");
        resp.then().statusCode(200);
        var names = resp.jsonPath().getList("name", String.class);
        var active = resp.jsonPath().getList("isActive", Boolean.class);
        for (int i = 0; i < names.size(); i++) {
            if ("default".equals(names.get(i))) {
                return Boolean.TRUE.equals(active.get(i));
            }
        }
        return false;
    }

    /** Returns the id of the item whose "name" equals {@code name} in a list endpoint. */
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
