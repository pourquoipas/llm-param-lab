package dev.gnius.llmlab;

import dev.gnius.llmlab.domain.EvaluationType;
import dev.gnius.llmlab.domain.RunResult;
import dev.gnius.llmlab.repository.RunResultRepository;
import dev.gnius.llmlab.service.ModelConfigService;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import jakarta.inject.Inject;
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

    @Inject
    ModelConfigService modelConfigService;

    @Inject
    RunResultRepository resultRepo;

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
        modelConfigService.deactivateAll();
        try {
            given().when().post("/api/suites/" + id + "/run").then().statusCode(400);
        } finally {
            delete(id);
            modelConfigService.ensureDefaultModel();
        }
    }

    @Test
    void runAllNoActiveModelReturns400() {
        Long id = create(validBody(uniqueName()));
        modelConfigService.deactivateAll();
        try {
            given().when().post("/api/run-all").then().statusCode(400);
        } finally {
            delete(id);
            modelConfigService.ensureDefaultModel();
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
    void summaryWithLegacyNullSeedResultReturns200GroupedUnderSeedZero() {
        Long id = create(validBody(uniqueName()));
        Long testCaseId = given().when().get("/api/suites/" + id)
                .then().statusCode(200)
                .extract().jsonPath().getLong("testCases[0].id");
        RunResult legacy = new RunResult();
        legacy.setSuiteId(id);
        legacy.setTestCaseId(testCaseId);
        legacy.setParamsJson("{}");
        legacy.setRawOutput("out");
        legacy.setLatencyMs(100L);
        legacy.setTokensIn(10);
        legacy.setTokensOut(20);
        legacy.setScore(0.9);
        legacy.setScoreReason("ok");
        legacy.setEvaluationType(EvaluationType.JUDGE_LLM);
        legacy.setPassed(true);
        // seed intentionally unset: simulates a row created before the seed sweep feature (004)
        RunResult saved = resultRepo.save(legacy);
        try {
            Response response = given().queryParam("suiteId", id)
                    .when().get("/api/results/summary");
            response.then().statusCode(200)
                    .body("seeds.size()", equalTo(1))
                    .body("seeds[0].seed", equalTo(0));
        } finally {
            resultRepo.delete(saved.getId());
            delete(id);
        }
    }

    @Test
    void summaryValidSuiteNoResultsReturnsEmpty() {
        Long id = create(validBody(uniqueName()));
        try {
            Response response = given().queryParam("suiteId", id)
                    .when().get("/api/results/summary");
            response.then().statusCode(200).body("seeds", equalTo(List.of()));
            org.junit.jupiter.api.Assertions.assertEquals(id, response.jsonPath().getLong("suiteId"));
        } finally {
            delete(id);
        }
    }

    // ---- R2: result deletion endpoints ----

    private static Long firstTestCaseId(Long suiteId) {
        return given().when().get("/api/suites/" + suiteId)
                .then().statusCode(200)
                .extract().jsonPath().getLong("testCases[0].id");
    }

    private Long seedResult(Long suiteId, Long testCaseId) {
        RunResult r = new RunResult();
        r.setSuiteId(suiteId);
        r.setTestCaseId(testCaseId);
        r.setParamsJson("{}");
        r.setRawOutput("out");
        r.setLatencyMs(1L);
        r.setTokensIn(1);
        r.setTokensOut(1);
        r.setScore(1.0);
        r.setScoreReason("ok");
        r.setEvaluationType(EvaluationType.EXACT_MATCH);
        r.setPassed(true);
        return resultRepo.save(r).getId();
    }

    private static int countResults(Long suiteId) {
        return given().queryParam("suiteId", suiteId)
                .when().get("/api/results")
                .then().statusCode(200)
                .extract().jsonPath().getList("").size();
    }

    @Test
    void deleteResultByIdReturns204AndRemovesRow() {
        Long id = create(validBody(uniqueName()));
        try {
            Long row = seedResult(id, firstTestCaseId(id));
            given().when().delete("/api/results/" + row).then().statusCode(204);
            org.junit.jupiter.api.Assertions.assertFalse(
                    resultRepo.findById(row).isPresent(), "row should be deleted");
            org.junit.jupiter.api.Assertions.assertEquals(0, countResults(id));
        } finally {
            delete(id);
        }
    }

    @Test
    void deleteResultsByIdsReturns204AndRemovesOnlyGiven() {
        Long id = create(validBody(uniqueName()));
        try {
            Long tc = firstTestCaseId(id);
            Long a = seedResult(id, tc);
            Long b = seedResult(id, tc);
            given().contentType(ContentType.JSON)
                    .body(Map.of("ids", List.of(a)))
                    .when().post("/api/results/delete")
                    .then().statusCode(204);
            org.junit.jupiter.api.Assertions.assertFalse(resultRepo.findById(a).isPresent());
            org.junit.jupiter.api.Assertions.assertTrue(resultRepo.findById(b).isPresent());
            org.junit.jupiter.api.Assertions.assertEquals(1, countResults(id));
        } finally {
            delete(id);
        }
    }

    @Test
    void deleteResultsByIdsEmptyReturns400() {
        given().contentType(ContentType.JSON)
                .body(Map.of("ids", List.of()))
                .when().post("/api/results/delete")
                .then().statusCode(400);
    }

    @Test
    void deleteResultsBySuiteReturns204AndRemovesSuiteRows() {
        Long id = create(validBody(uniqueName()));
        try {
            Long tc = firstTestCaseId(id);
            seedResult(id, tc);
            seedResult(id, tc);
            given().queryParam("suiteId", id)
                    .when().delete("/api/results")
                    .then().statusCode(204);
            org.junit.jupiter.api.Assertions.assertEquals(0, countResults(id));
        } finally {
            delete(id);
        }
    }

    @Test
    void deleteResultsBySuiteWithoutIdReturns400() {
        given().when().delete("/api/results").then().statusCode(400);
    }

    @Test
    void deleteAllResultsReturns204AndRemovesEverything() {
        Long id = create(validBody(uniqueName()));
        try {
            Long tc = firstTestCaseId(id);
            seedResult(id, tc);
            given().when().delete("/api/results/all").then().statusCode(204);
            org.junit.jupiter.api.Assertions.assertEquals(0, countResults(id));
        } finally {
            delete(id);
        }
    }
}
