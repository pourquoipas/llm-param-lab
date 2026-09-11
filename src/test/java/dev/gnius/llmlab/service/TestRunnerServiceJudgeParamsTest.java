package dev.gnius.llmlab.service;

import dev.gnius.llmlab.domain.Judge;
import dev.gnius.llmlab.domain.ModelProvider;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.openai.OpenAiChatRequestParameters;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Pure tests for the savable judge params: judge temperature defaults to 0.0 when unset,
 * and the savable topP / seed are passed through provider-specifically. No DB, no model.
 */
class TestRunnerServiceJudgeParamsTest {

    private final TestRunnerService service = new TestRunnerService();

    @Test
    void judgeDefaultsToZeroTemperature() {
        Judge judge = new Judge("all around");
        ChatRequestParameters p = service.judgeParameters(judge, ModelProvider.OPENAI_COMPATIBLE);
        Assertions.assertEquals(0.0, p.temperature());
        Assertions.assertNull(p.topP());
    }

    @Test
    void judgeSavableParamsPassedThrough() {
        Judge judge = new Judge("all around");
        judge.setTemperature(0.3);
        judge.setTopP(0.9);
        judge.setSeed(42);
        OpenAiChatRequestParameters p =
                (OpenAiChatRequestParameters) service.judgeParameters(judge, ModelProvider.OPENAI_COMPATIBLE);
        Assertions.assertEquals(0.3, p.temperature());
        Assertions.assertEquals(0.9, p.topP());
        Assertions.assertEquals(42, p.seed());
    }

    @Test
    void judgeSeedAppliedForOllamaToo() {
        Judge judge = new Judge("all around");
        judge.setSeed(7);
        dev.langchain4j.model.ollama.OllamaChatRequestParameters p =
                (dev.langchain4j.model.ollama.OllamaChatRequestParameters)
                        service.judgeParameters(judge, ModelProvider.OLLAMA);
        Assertions.assertEquals(0.0, p.temperature());
        Assertions.assertEquals(7, p.seed());
    }
}
