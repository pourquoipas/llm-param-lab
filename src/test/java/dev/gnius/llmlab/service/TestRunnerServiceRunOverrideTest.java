package dev.gnius.llmlab.service;

import dev.gnius.llmlab.config.ModelFactory;
import dev.gnius.llmlab.domain.EvaluationType;
import dev.gnius.llmlab.domain.Judge;
import dev.gnius.llmlab.domain.ModelConfig;
import dev.gnius.llmlab.domain.ModelProvider;
import dev.gnius.llmlab.domain.TestCase;
import dev.gnius.llmlab.domain.TestSuite;
import dev.gnius.llmlab.repository.ModelConfigRepository;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Optional;

/**
 * Offline tests for the J4 run-time judge override: the judge's topK is applied on top of its
 * saved params, and an override judge wins over the suite's own evaluation strategy (including
 * expected-output matching). No DB, no network — the model and repositories are faked.
 */
class TestRunnerServiceRunOverrideTest {

    private static final ModelConfig MODEL =
            new ModelConfig("m", ModelProvider.OPENAI_COMPATIBLE, "http://localhost:1/v1", null, "m");

    private final TestRunnerService service = new TestRunnerService();

    // ---- judgeParameters: topK is applied on top of the judge's saved params ----

    @Test
    void judgeParameters_topKPassedThrough_OpenAI() {
        Judge judge = new Judge("j");
        judge.setTemperature(0.2);
        judge.setTopP(0.9);
        judge.setSeed(42);
        dev.langchain4j.model.openai.OpenAiChatRequestParameters p =
                (dev.langchain4j.model.openai.OpenAiChatRequestParameters)
                        service.judgeParameters(judge, ModelProvider.OPENAI_COMPATIBLE, 5);
        Assertions.assertEquals(5, p.topK());
        // saved params still apply
        Assertions.assertEquals(0.2, p.temperature());
        Assertions.assertEquals(0.9, p.topP());
        Assertions.assertEquals(42, p.seed());
    }

    @Test
    void judgeParameters_nullTopK_meansModelDefault() {
        Judge judge = new Judge("j");
        ChatRequestParameters p =
                service.judgeParameters(judge, ModelProvider.OPENAI_COMPATIBLE, null);
        Assertions.assertNull(p.topK());
    }

    @Test
    void judgeParameters_topKPassedThrough_Ollama() {
        Judge judge = new Judge("j");
        dev.langchain4j.model.ollama.OllamaChatRequestParameters p =
                (dev.langchain4j.model.ollama.OllamaChatRequestParameters)
                        service.judgeParameters(judge, ModelProvider.OLLAMA, 3);
        Assertions.assertEquals(3, p.topK());
    }

    // ---- evaluate: override judge wins over expected-output + topK reaches the call ----

    /** Fake judge model: records the request and replies with a fixed JSON verdict. */
    private static final class FakeJudgeModel implements ChatModel {
        ChatRequestParameters lastParams;
        @Override
        public ChatResponse chat(ChatRequest request) {
            this.lastParams = request.parameters();
            return ChatResponse.builder()
                    .aiMessage(AiMessage.from("{\"score\":0.8,\"reason\":\"ok\"}"))
                    .tokenUsage(new TokenUsage(1, 1))
                    .build();
        }
    }

    @Test
    void evaluate_overrideJudgeWinsOverExpectedOutput_andTopKApplied() {
        FakeJudgeModel fake = new FakeJudgeModel();
        // Stub the two collaborators evaluateWithJudge depends on (no DB).
        service.modelConfigRepository = new ModelConfigRepository() {
            @Override
            public Optional<ModelConfig> findActive() {
                return Optional.of(MODEL);
            }
        };
        service.modelFactory = new ModelFactory() {
            @Override
            public ChatModel create(ModelConfig config) {
                return fake;
            }
        };

        // Suite WOULD match by exact output ("raw" == "raw"), but the override must win.
        TestSuite suite = new TestSuite("s");
        suite.setExpectedOutput("raw");
        TestCase tc = new TestCase(1L, "c", null, "hi", 0);

        Judge override = new Judge("ovr");
        override.setTemperature(0.1);
        override.setPrompt("review {{response}}"); // non-blank → no appConfig fallback

        TestRunnerService.Evaluation e = service.evaluate(suite, tc, "raw", override, 7);

        Assertions.assertEquals(EvaluationType.JUDGE_LLM, e.type());
        Assertions.assertEquals(0.8, e.score());
        Assertions.assertEquals(7, fake.lastParams.topK());
        // the override judge's saved temperature is used, not a hardcoded value
        Assertions.assertEquals(0.1, fake.lastParams.temperature());
    }

    @Test
    void evaluate_noOverride_fallsBackToExpectedOutput() {
        // No override judge: the suite's own strategy applies (exact match here).
        TestSuite suite = new TestSuite("s");
        suite.setExpectedOutput("raw");
        TestCase tc = new TestCase(1L, "c", null, "hi", 0);

        TestRunnerService.Evaluation e = service.evaluate(suite, tc, "raw", null, null);

        Assertions.assertEquals(EvaluationType.EXACT_MATCH, e.type());
        Assertions.assertEquals(1.0, e.score());
    }
}
