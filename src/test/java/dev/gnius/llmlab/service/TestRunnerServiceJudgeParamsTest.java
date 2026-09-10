package dev.gnius.llmlab.service;

import dev.gnius.llmlab.domain.ModelProvider;
import dev.gnius.llmlab.domain.TestSuite;
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
        TestSuite suite = new TestSuite("s");
        ChatRequestParameters p = service.judgeParameters(suite, ModelProvider.OPENAI_COMPATIBLE);
        Assertions.assertEquals(0.0, p.temperature());
        Assertions.assertNull(p.topP());
    }

    @Test
    void judgeSavableParamsPassedThrough() {
        TestSuite suite = new TestSuite("s");
        suite.setJudgeTemperature(0.3);
        suite.setJudgeTopP(0.9);
        suite.setJudgeSeed(42);
        OpenAiChatRequestParameters p =
                (OpenAiChatRequestParameters) service.judgeParameters(suite, ModelProvider.OPENAI_COMPATIBLE);
        Assertions.assertEquals(0.3, p.temperature());
        Assertions.assertEquals(0.9, p.topP());
        Assertions.assertEquals(42, p.seed());
    }

    @Test
    void judgeSeedAppliedForOllamaToo() {
        TestSuite suite = new TestSuite("s");
        suite.setJudgeSeed(7);
        dev.langchain4j.model.ollama.OllamaChatRequestParameters p =
                (dev.langchain4j.model.ollama.OllamaChatRequestParameters)
                        service.judgeParameters(suite, ModelProvider.OLLAMA);
        Assertions.assertEquals(0.0, p.temperature());
        Assertions.assertEquals(7, p.seed());
    }
}
