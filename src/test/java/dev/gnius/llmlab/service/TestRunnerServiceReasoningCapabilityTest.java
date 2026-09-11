package dev.gnius.llmlab.service;

import dev.gnius.llmlab.domain.EvaluationType;
import dev.gnius.llmlab.domain.ModelConfig;
import dev.gnius.llmlab.domain.ModelProvider;
import dev.gnius.llmlab.domain.RunResult;
import dev.gnius.llmlab.domain.TestCase;
import dev.gnius.llmlab.domain.TestSuite;
import dev.gnius.llmlab.repository.ModelConfigRepository;
import dev.gnius.llmlab.repository.RunResultRepository;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.exception.TimeoutException;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openai.OpenAiChatRequestParameters;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Offline tests for the reasoning-capability retry (J8). A model that advertises a reasoning
 * effort but rejects it (provider error mentioning "reasoning") gets its capability flag flipped
 * to false (persisted) and the call retried once without the effort. A non-reasoning error must
 * NOT flip the flag and must NOT retry. No DB, no network: the model and repositories are faked.
 */
class TestRunnerServiceReasoningCapabilityTest {

    /** Fake model: rejects any request carrying a reasoning effort, succeeds otherwise. */
    private static ChatModel effortRejectingModel() {
        return new ChatModel() {
            @Override
            public ChatResponse chat(ChatRequest request) {
                OpenAiChatRequestParameters p = (OpenAiChatRequestParameters) request.parameters();
                if (p.reasoningEffort() != null) {
                    throw new RuntimeException(
                            "400 Unrecognized request argument supplied: reasoning_effort");
                }
                return ChatResponse.builder()
                        .aiMessage(AiMessage.from("ok"))
                        .tokenUsage(new TokenUsage(10, 5))
                        .build();
            }
        };
    }

    private static TestSuite plainSuite() {
        // no expected output, no judge → a successful combo evaluates to SKIPPED
        return new TestSuite("s");
    }

    private static TestCase tc() {
        return new TestCase(1L, "c", null, "hi", 0);
    }

    private static ModelConfig effortModel() {
        ModelConfig m = new ModelConfig("m", ModelProvider.OPENAI_COMPATIBLE,
                "http://localhost:1/v1", null, "m");
        m.setReasoningEffort("high");
        // reasoningCapability defaults to true
        return m;
    }

    /** Stub capturing save() and serving findById(); never touches the EntityManager. */
    private static ModelConfigRepository modelRepo(List<ModelConfig> saved, ModelConfig backing) {
        return new ModelConfigRepository() {
            @Override
            public ModelConfig save(ModelConfig entity) {
                saved.add(entity);
                return entity;
            }

            @Override
            public Optional<ModelConfig> findById(Long id) {
                return Optional.ofNullable(backing);
            }
        };
    }

    private static TestRunnerService service(List<RunResult> results, List<ModelConfig> models,
                                             ModelConfig backing) {
        TestRunnerService service = new TestRunnerService();
        service.runResultRepository = new RunResultRepository() {
            @Override
            public RunResult save(RunResult entity) {
                results.add(entity);
                return entity;
            }
        };
        service.modelConfigRepository = modelRepo(models, backing);
        return service;
    }

    @Test
    void reasoningUnsupported_firstRunFlipsCapabilityAndRetriesWithoutEffort() {
        List<RunResult> results = new ArrayList<>();
        List<ModelConfig> savedModels = new ArrayList<>();
        ModelConfig model = effortModel();
        TestRunnerService service = service(results, savedModels, model);

        RunResult result = Assertions.assertDoesNotThrow(() ->
                service.runOne(plainSuite(), tc(), Map.of(), 42, model, effortRejectingModel()));

        // The retry (no effort) succeeded → not an ERROR, and the output was captured.
        Assertions.assertEquals(EvaluationType.SKIPPED, result.getEvaluationType());
        Assertions.assertEquals("ok", result.getRawOutput());
        // The capability flag was flipped off and persisted exactly once.
        Assertions.assertFalse(model.isReasoningCapability());
        Assertions.assertEquals(1, savedModels.size());
        Assertions.assertSame(model, savedModels.get(0));
    }

    @Test
    void flaggedModel_secondRunSucceedsWithoutEffortAndWithoutSave() {
        List<RunResult> results = new ArrayList<>();
        List<ModelConfig> savedModels = new ArrayList<>();
        ModelConfig model = effortModel();
        model.setReasoningCapability(false); // already known to reject effort
        TestRunnerService service = service(results, savedModels, model);

        RunResult result = Assertions.assertDoesNotThrow(() ->
                service.runOne(plainSuite(), tc(), Map.of(), 42, model, effortRejectingModel()));

        // No effort is sent (flag is off) → the model accepts the call on the first try.
        Assertions.assertEquals(EvaluationType.SKIPPED, result.getEvaluationType());
        Assertions.assertEquals("ok", result.getRawOutput());
        // Nothing to flip → no save.
        Assertions.assertTrue(savedModels.isEmpty());
        Assertions.assertFalse(model.isReasoningCapability());
    }

    @Test
    void nonReasoningError_doesNotFlipCapabilityAndDoesNotRetry() {
        List<RunResult> results = new ArrayList<>();
        List<ModelConfig> savedModels = new ArrayList<>();
        ModelConfig model = effortModel();
        TestRunnerService service = service(results, savedModels, model);

        ChatModel flaky = new ChatModel() {
            @Override
            public ChatResponse chat(ChatRequest request) {
                throw new TimeoutException("request timed out");
            }
        };

        RunResult result = Assertions.assertDoesNotThrow(() ->
                service.runOne(plainSuite(), tc(), Map.of(), 42, model, flaky));

        // A timeout is not a reasoning rejection: recorded as ERROR, flag untouched, no save.
        Assertions.assertEquals(EvaluationType.ERROR, result.getEvaluationType());
        Assertions.assertTrue(result.getScoreReason().contains("TimeoutException"));
        Assertions.assertTrue(model.isReasoningCapability());
        Assertions.assertTrue(savedModels.isEmpty());
    }
}
