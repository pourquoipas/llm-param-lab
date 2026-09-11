package dev.gnius.llmlab;

import dev.gnius.llmlab.dto.JudgeConfigRequest;
import dev.gnius.llmlab.dto.JudgeConfigResponse;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * End-to-end REST tests for the judge registry ({@code /api/judges}) against the
 * in-memory H2 test DB. Each test uses a unique judge name so the shared test
 * database stays independent; created judges are cleaned up in {@code finally}.
 */
@QuarkusTest
class JudgeResourceTest {

    static final AtomicInteger SEQ = new AtomicInteger();

    private static String uniqueName() {
        return "rest-judge-" + SEQ.incrementAndGet();
    }

    private static JudgeConfigRequest req(String name) {
        return new JudgeConfigRequest(name, null, null, 0.2, 0.9, null);
    }

    private static Long create(String name) {
        return given()
                .contentType(ContentType.JSON).body(req(name))
                .when().post("/api/judges")
                .then().statusCode(201)
                .extract().body().as(JudgeConfigResponse.class).id();
    }

    private static void delete(Long id) {
        given().when().delete("/api/judges/" + id).then().statusCode(204);
    }

    @Test
    void createReturns201AndAppearsInList() {
        String name = uniqueName();
        JudgeConfigResponse created = given()
                .contentType(ContentType.JSON).body(req(name))
                .when().post("/api/judges")
                .then().statusCode(201)
                .extract().body().as(JudgeConfigResponse.class);
        assertNotNull(created.id());
        assertEquals(name, created.name());
        assertEquals(0.2, created.temperature());
        assertEquals(0.9, created.topP());

        given().when().get("/api/judges")
                .then().statusCode(200)
                .body("name", hasItem(name));
    }

    @Test
    void createWithDuplicateNameReturns409() {
        String name = uniqueName();
        Long id = create(name);
        try {
            given().contentType(ContentType.JSON).body(req(name))
                    .when().post("/api/judges")
                    .then().statusCode(409);
        } finally {
            delete(id);
        }
    }

    @Test
    void createWithMissingNameReturns400() {
        given().contentType(ContentType.JSON)
                .body(new JudgeConfigRequest(null, null, null, 0.0, null, null))
                .when().post("/api/judges")
                .then().statusCode(400);
    }

    @Test
    void getReturnsJudgeById() {
        String name = uniqueName();
        Long id = create(name);
        try {
            given().when().get("/api/judges/" + id)
                    .then().statusCode(200)
                    .body("name", org.hamcrest.Matchers.equalTo(name));
        } finally {
            delete(id);
        }
    }

    @Test
    void getNotFoundReturns404() {
        given().when().get("/api/judges/999999").then().statusCode(404);
    }

    @Test
    void updateChangesFields() {
        String name = uniqueName();
        Long id = create(name);
        try {
            JudgeConfigRequest updated = new JudgeConfigRequest(name, null, "system prompt", 0.5, 0.8, 42);
            JudgeConfigResponse resp = given()
                    .contentType(ContentType.JSON).body(updated)
                    .when().put("/api/judges/" + id)
                    .then().statusCode(200)
                    .extract().body().as(JudgeConfigResponse.class);
            assertEquals("system prompt", resp.prompt());
            assertEquals(0.5, resp.temperature());
            assertEquals(42, resp.seed());
        } finally {
            delete(id);
        }
    }

    @Test
    void updateNotFoundReturns404() {
        given().contentType(ContentType.JSON).body(req(uniqueName()))
                .when().put("/api/judges/999999")
                .then().statusCode(404);
    }

    @Test
    void updateWithDuplicateNameReturns409() {
        String other = uniqueName();
        String name = uniqueName();
        Long otherId = create(other);
        Long id = create(name);
        try {
            // Rename the new judge onto the existing one's name → 409.
            given().contentType(ContentType.JSON)
                    .body(new JudgeConfigRequest(other, null, null, 0.1, null, null))
                    .when().put("/api/judges/" + id)
                    .then().statusCode(409);
        } finally {
            delete(otherId);
            delete(id);
        }
    }

    @Test
    void deleteRemovesJudge() {
        String name = uniqueName();
        Long id = create(name);
        delete(id);
        given().when().get("/api/judges")
                .then().statusCode(200)
                .body("name", not(hasItem(name)));
    }

    @Test
    void deleteNotFoundReturns404() {
        given().when().delete("/api/judges/999999").then().statusCode(404);
    }
}
