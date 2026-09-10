package dev.gnius.llmlab;

import dev.gnius.llmlab.domain.EvaluationType;
import dev.gnius.llmlab.domain.RunResult;
import dev.gnius.llmlab.repository.RunResultRepository;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static io.restassured.RestAssured.given;

/**
 * Offline tests for the results report export. Excel (.xlsx) is covered here (R4);
 * PDF is added in R5. Runs against the in-memory H2 test DB, no LLM involved.
 */
@QuarkusTest
class ReportExportTest {

    static final String XLSX_MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    static final String PDF_MIME = "application/pdf";

    static final AtomicInteger SEQ = new AtomicInteger();

    @Inject
    RunResultRepository resultRepo;

    private static String uniqueName() {
        return "report-suite-" + SEQ.incrementAndGet();
    }

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
                Map.of("paramName", "temperature", "values", "[0.3, 0.7]")));
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

    private static Long firstTestCaseId(Long suiteId) {
        return given().when().get("/api/suites/" + suiteId)
                .then().statusCode(200)
                .extract().jsonPath().getLong("testCases[0].id");
    }

    private Long seedResult(Long suiteId, Long testCaseId, int seed, double score) {
        RunResult r = new RunResult();
        r.setSuiteId(suiteId);
        r.setTestCaseId(testCaseId);
        r.setSeed(seed);
        r.setParamsJson("{\"temperature\":0.3}");
        r.setRawOutput("hello world");
        r.setLatencyMs(120L);
        r.setTokensIn(10);
        r.setTokensOut(20);
        r.setScore(score);
        r.setScoreReason("ok");
        r.setEvaluationType(EvaluationType.EXACT_MATCH);
        r.setPassed(true);
        return resultRepo.save(r).getId();
    }

    @Test
    void xlsxExportReturnsValidWorkbook() throws Exception {
        Long id = create(validBody(uniqueName()));
        try {
            Long tc = firstTestCaseId(id);
            seedResult(id, tc, 1, 0.9);
            seedResult(id, tc, 2, 0.5);

            Response response = given().queryParam("suiteId", id)
                    .when().get("/api/results/export/xlsx");
            response.then().statusCode(200).contentType(XLSX_MIME);

            byte[] bytes = response.getBody().asByteArray();
            // .xlsx is a ZIP container: magic bytes are 'P' 'K'
            Assertions.assertTrue(bytes.length > 2 && bytes[0] == 'P' && bytes[1] == 'K',
                    "body should start with PK (zip magic)");

            try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
                Assertions.assertTrue(wb.getNumberOfSheets() >= 1, "workbook should have sheets");
                // "Results" sheet: header + 2 seeded rows
                org.apache.poi.ss.usermodel.Sheet results = wb.getSheet("Results");
                Assertions.assertNotNull(results, "Results sheet should exist");
                Assertions.assertEquals(3, results.getLastRowNum() + 1, "1 header + 2 data rows");
            }
        } finally {
            delete(id);
        }
    }

    @Test
    void xlsxExportMissingSuiteIdReturns400() {
        given().when().get("/api/results/export/xlsx").then().statusCode(400);
    }

    @Test
    void xlsxExportUnknownSuiteReturns404() {
        given().queryParam("suiteId", 999999)
                .when().get("/api/results/export/xlsx")
                .then().statusCode(404);
    }

    @Test
    void pdfExportReturnsValidDocument() {
        Long id = create(validBody(uniqueName()));
        try {
            Long tc = firstTestCaseId(id);
            seedResult(id, tc, 1, 0.9);
            seedResult(id, tc, 2, 0.5);

            Response response = given().queryParam("suiteId", id)
                    .when().get("/api/results/export/pdf");
            response.then().statusCode(200).contentType(PDF_MIME);

            byte[] bytes = response.getBody().asByteArray();
            // PDF files start with the '%PDF-' magic header
            Assertions.assertTrue(bytes.length > 5 && new String(bytes, 0, 5).equals("%PDF-"),
                    "body should start with %PDF-");
        } finally {
            delete(id);
        }
    }

    @Test
    void pdfExportMissingSuiteIdReturns400() {
        given().when().get("/api/results/export/pdf").then().statusCode(400);
    }

    @Test
    void pdfExportUnknownSuiteReturns404() {
        given().queryParam("suiteId", 999999)
                .when().get("/api/results/export/pdf")
                .then().statusCode(404);
    }
}
