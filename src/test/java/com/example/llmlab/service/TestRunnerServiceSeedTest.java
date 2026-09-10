package com.example.llmlab.service;

import com.example.llmlab.domain.ModelProvider;
import com.example.llmlab.domain.TestSuite;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.openai.OpenAiChatRequestParameters;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

/**
 * Offline tests for the seed-sweep plumbing: {@link TestRunnerService#resolveSeeds}
 * (empty → one generated seed; explicit → as-is) and the provider-aware seed applied
 * to the model-under-test params. No DB, no model call.
 */
class TestRunnerServiceSeedTest {

    private final TestRunnerService service = new TestRunnerService();

    @Test
    void emptySeedsGeneratesOne() {
        TestSuite suite = new TestSuite("s");
        List<Integer> seeds = service.resolveSeeds(suite);
        Assertions.assertEquals(1, seeds.size());
        Assertions.assertNotNull(seeds.get(0));
    }

    @Test
    void explicitSeedsUsedAsIs() {
        TestSuite suite = new TestSuite("s");
        suite.setSeeds("[7, 42]");
        Assertions.assertEquals(List.of(7, 42), service.resolveSeeds(suite));
    }

    @Test
    void invalidSeedJsonFallsBackToGenerated() {
        TestSuite suite = new TestSuite("s");
        suite.setSeeds("not-json");
        Assertions.assertEquals(1, service.resolveSeeds(suite).size());
    }

    @Test
    void buildParametersAppliesSeedForOpenAi() {
        ChatRequestParameters p = service.buildParameters(
                Map.of("temperature", 0.5), ModelProvider.OPENAI_COMPATIBLE, 123);
        Assertions.assertEquals(0.5, p.temperature());
        Assertions.assertEquals(123, ((OpenAiChatRequestParameters) p).seed());
    }

    @Test
    void buildParametersAppliesSeedForOllama() {
        ChatRequestParameters p = service.buildParameters(
                Map.of("topP", 0.9), ModelProvider.OLLAMA, 99);
        Assertions.assertEquals(0.9, p.topP());
        Assertions.assertEquals(99,
                ((dev.langchain4j.model.ollama.OllamaChatRequestParameters) p).seed());
    }
}
