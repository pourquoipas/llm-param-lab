package dev.gnius.llmlab;

import dev.gnius.llmlab.domain.ModelProvider;
import dev.gnius.llmlab.dto.ModelConfigRequest;
import dev.gnius.llmlab.dto.ModelConfigResponse;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * End-to-end REST tests for {@code /api/models} against the in-memory H2 test DB.
 * Each test uses a unique model name so the shared test database stays independent.
 */
@QuarkusTest
class ModelConfigResourceTest {

    static final AtomicInteger SEQ = new AtomicInteger();

    private static String uniqueName() {
        return "rest-model-" + SEQ.incrementAndGet();
    }

    private static ModelConfigRequest req(String name) {
        return new ModelConfigRequest(name, ModelProvider.OLLAMA, "http://localhost:11434", null, "llama3.1");
    }

    private static Long create(String name) {
        return given()
                .contentType(ContentType.JSON).body(req(name))
                .when().post("/api/models")
                .then().statusCode(201)
                .extract().body().as(ModelConfigResponse.class).id();
    }

    private static void delete(Long id) {
        given().when().delete("/api/models/" + id).then().statusCode(204);
    }

    @Test
    void createReturns201AndAppearsInList() {
        String name = uniqueName();
        ModelConfigResponse created = given()
                .contentType(ContentType.JSON).body(req(name))
                .when().post("/api/models")
                .then().statusCode(201)
                .extract().body().as(ModelConfigResponse.class);
        assertNotNull(created.id());
        assertEquals(name, created.name());
        assertEquals(ModelProvider.OLLAMA, created.provider());
        assertFalse(created.isActive());

        given().when().get("/api/models")
                .then().statusCode(200)
                .body("name", hasItem(name));
    }

    @Test
    void createWithDuplicateNameReturns409() {
        String name = uniqueName();
        Long id = create(name);
        try {
            given().contentType(ContentType.JSON).body(req(name))
                    .when().post("/api/models")
                    .then().statusCode(409);
        } finally {
            delete(id);
        }
    }

    @Test
    void createWithMissingFieldReturns400() {
        ModelConfigRequest bad = new ModelConfigRequest("x", ModelProvider.OLLAMA, null, null, "m");
        given().contentType(ContentType.JSON).body(bad)
                .when().post("/api/models")
                .then().statusCode(400);
    }

    @Test
    void updateChangesFields() {
        String name = uniqueName();
        Long id = create(name);
        try {
            ModelConfigRequest updated = new ModelConfigRequest(name, ModelProvider.OPENAI_COMPATIBLE,
                    "https://api.openai.com/v1", "key", "gpt-4o-mini");
            ModelConfigResponse resp = given()
                    .contentType(ContentType.JSON).body(updated)
                    .when().put("/api/models/" + id)
                    .then().statusCode(200)
                    .extract().body().as(ModelConfigResponse.class);
            assertEquals(ModelProvider.OPENAI_COMPATIBLE, resp.provider());
            assertEquals("gpt-4o-mini", resp.modelName());
        } finally {
            delete(id);
        }
    }

    @Test
    void updateNotFoundReturns404() {
        given().contentType(ContentType.JSON).body(req(uniqueName()))
                .when().put("/api/models/999999")
                .then().statusCode(404);
    }

    @Test
    void deleteRemovesModel() {
        String name = uniqueName();
        Long id = create(name);
        delete(id);
        given().when().get("/api/models")
                .then().statusCode(200)
                .body("name", not(hasItem(name)));
    }

    @Test
    void activateFlipsFlags() {
        Long a = create(uniqueName());
        Long b = create(uniqueName());
        try {
            given().when().post("/api/models/" + a + "/activate")
                    .then().statusCode(200)
                    .body("isActive", equalTo(true));

            given().when().post("/api/models/" + b + "/activate")
                    .then().statusCode(200)
                    .body("isActive", equalTo(true));

            // A must now be deactivated
            ModelConfigResponse[] models = given().when().get("/api/models")
                    .then().statusCode(200)
                    .extract().body().as(ModelConfigResponse[].class);
            ModelConfigResponse modelA = java.util.Arrays.stream(models)
                    .filter(m -> a.equals(m.id())).findFirst().orElseThrow();
            assertFalse(modelA.isActive());
        } finally {
            delete(a);
            delete(b);
        }
    }

    @Test
    void activateNotFoundReturns404() {
        given().when().post("/api/models/999999/activate").then().statusCode(404);
    }
}
