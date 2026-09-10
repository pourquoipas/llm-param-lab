package dev.gnius.llmlab.service;

import dev.gnius.llmlab.domain.ModelProvider;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

/**
 * Offline test for I5: the runner must NOT inject a default token limit. Only a
 * swept {@code maxTokens} value becomes a {@code maxOutputTokens} cap; otherwise the
 * cap is left null so the model's own default applies. No DB, no model call.
 */
class TestRunnerServiceNoDefaultTokenCapTest {

    private final TestRunnerService service = new TestRunnerService();

    @Test
    void noSweptMaxTokens_meansNoCap() {
        ChatRequestParameters p = service.buildParameters(
                Map.of("temperature", 0.5), ModelProvider.OPENAI_COMPATIBLE, null);
        // No cap injected — the model's own default is used.
        Assertions.assertNull(p.maxOutputTokens());
        // The swept param is still applied.
        Assertions.assertEquals(0.5, p.temperature());
    }

    @Test
    void sweptMaxTokens_becomesCap() {
        ChatRequestParameters p = service.buildParameters(
                Map.of("maxTokens", 256), ModelProvider.OPENAI_COMPATIBLE, null);
        Assertions.assertEquals(256, p.maxOutputTokens());
    }
}
