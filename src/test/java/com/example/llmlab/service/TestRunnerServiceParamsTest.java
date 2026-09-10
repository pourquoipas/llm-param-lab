package com.example.llmlab.service;

import dev.langchain4j.model.chat.request.ChatRequestParameters;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Pure tests for {@link TestRunnerService#buildParameters}: the sweepable chat-level
 * params map onto the generic {@link ChatRequestParameters} (works for OpenAI + Ollama).
 * No DB, no model call.
 */
class TestRunnerServiceParamsTest {

    private final TestRunnerService service = new TestRunnerService();

    @Test
    void mapsAllSweepableChatParams() {
        Map<String, Object> combo = new LinkedHashMap<>();
        combo.put("temperature", 0.7);
        combo.put("topP", 0.9);
        combo.put("topK", 40);
        combo.put("frequencyPenalty", 0.5);
        combo.put("presencePenalty", 0.25);
        combo.put("maxTokens", 512);

        ChatRequestParameters p = service.buildParameters(combo);
        Assertions.assertEquals(0.7, p.temperature());
        Assertions.assertEquals(0.9, p.topP());
        Assertions.assertEquals(40, p.topK());
        Assertions.assertEquals(0.5, p.frequencyPenalty());
        Assertions.assertEquals(0.25, p.presencePenalty());
        Assertions.assertEquals(512, p.maxOutputTokens());
    }

    @Test
    void emptyComboYieldsAllNullParams() {
        ChatRequestParameters p = service.buildParameters(Map.of());
        Assertions.assertNull(p.temperature());
        Assertions.assertNull(p.topP());
        Assertions.assertNull(p.topK());
        Assertions.assertNull(p.maxOutputTokens());
    }

    @Test
    void unknownParamIgnored() {
        ChatRequestParameters p = service.buildParameters(Map.of("bogus", 1));
        Assertions.assertNull(p.temperature());
    }
}
