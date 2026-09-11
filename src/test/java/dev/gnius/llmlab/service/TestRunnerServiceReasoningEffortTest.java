package dev.gnius.llmlab.service;

import dev.gnius.llmlab.domain.Judge;
import dev.gnius.llmlab.domain.ModelProvider;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.openai.OpenAiChatRequestParameters;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

/**
 * Offline tests for the per-model reasoning effort (J7): an OpenAI model's saved
 * {@code reasoningEffort} is threaded into the test-call and judge-call params; Ollama
 * params are unaffected; a null effort means "not set" (the model's own default).
 * No DB, no model call.
 */
class TestRunnerServiceReasoningEffortTest {

    private final TestRunnerService service = new TestRunnerService();

    @Test
    void buildParametersAppliesEffortForOpenAi() {
        ChatRequestParameters p = service.buildParameters(
                Map.of("temperature", 0.5), ModelProvider.OPENAI_COMPATIBLE, null, "high");
        Assertions.assertEquals("high", ((OpenAiChatRequestParameters) p).reasoningEffort());
    }

    @Test
    void buildParametersOmitEffortForOllama() {
        ChatRequestParameters p = service.buildParameters(
                Map.of("temperature", 0.5), ModelProvider.OLLAMA, null, "high");
        // Ollama has no reasoning-effort concept: the param stays an Ollama one.
        Assertions.assertTrue(p instanceof dev.langchain4j.model.ollama.OllamaChatRequestParameters);
    }

    @Test
    void buildParametersNullEffortMeansUnset() {
        ChatRequestParameters p = service.buildParameters(
                Map.of(), ModelProvider.OPENAI_COMPATIBLE, null, null);
        Assertions.assertNull(((OpenAiChatRequestParameters) p).reasoningEffort());
    }

    @Test
    void judgeParametersAppliesEffortForOpenAi() {
        Judge judge = new Judge("all around");
        ChatRequestParameters p = service.judgeParameters(
                judge, ModelProvider.OPENAI_COMPATIBLE, null, "medium");
        Assertions.assertEquals("medium", ((OpenAiChatRequestParameters) p).reasoningEffort());
    }

    @Test
    void judgeParametersNullEffortMeansUnset() {
        Judge judge = new Judge("all around");
        ChatRequestParameters p = service.judgeParameters(
                judge, ModelProvider.OPENAI_COMPATIBLE, null, null);
        Assertions.assertNull(((OpenAiChatRequestParameters) p).reasoningEffort());
    }
}
