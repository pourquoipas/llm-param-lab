package com.example.llmlab.service;

import com.example.llmlab.domain.EvaluationType;
import com.example.llmlab.domain.ModelConfig;
import com.example.llmlab.domain.ModelProvider;
import com.example.llmlab.domain.RunResult;
import com.example.llmlab.domain.TestCase;
import com.example.llmlab.domain.TestSuite;
import com.example.llmlab.repository.RunResultRepository;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.exception.TimeoutException;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Offline tests for the resilient run logic: a single combo that fails (e.g. an LLM
 * request timeout) is recorded as an {@code ERROR} result and does NOT abort the run,
 * so the remaining combos still execute. No DB and no network — the model is faked and
 * the repository is an in-memory capture stub.
 */
class TestRunnerServiceResilienceTest {

    private static final ModelConfig MODEL =
            new ModelConfig("m", ModelProvider.OPENAI_COMPATIBLE, "http://localhost:1/v1", null, "m");

    /** In-memory capture stub: records saved results, never touches the EntityManager. */
    private static RunResultRepository capturingRepo(List<RunResult> saved) {
        return new RunResultRepository() {
            @Override
            public RunResult save(RunResult entity) {
                saved.add(entity);
                return entity;
            }
        };
    }

    private static TestRunnerService serviceWithRepo(List<RunResult> saved) {
        TestRunnerService service = new TestRunnerService();
        service.runResultRepository = capturingRepo(saved);
        return service;
    }

    private static TestSuite plainSuite() {
        // no expected output, no judge → a successful combo evaluates to SKIPPED
        return new TestSuite("s");
    }

    private static TestCase tc() {
        return new TestCase(1L, "c", null, "hi", 0);
    }

    @Test
    void failingModel_recordsErrorResult_andDoesNotThrow() {
        List<RunResult> saved = new ArrayList<>();
        TestRunnerService service = serviceWithRepo(saved);
        ChatModel throwing = new ChatModel() {
            @Override
            public ChatResponse chat(ChatRequest request) {
                throw new TimeoutException("request timed out");
            }
        };

        RunResult result = Assertions.assertDoesNotThrow(
                () -> service.runOne(plainSuite(), tc(), Map.of(), 42, MODEL, throwing));

        Assertions.assertEquals(1, saved.size());
        Assertions.assertEquals(EvaluationType.ERROR, result.getEvaluationType());
        Assertions.assertNull(result.getScore());
        Assertions.assertFalse(result.getPassed());
        Assertions.assertTrue(result.getScoreReason().contains("TimeoutException"));
        Assertions.assertEquals(42, result.getSeed());
    }

    @Test
    void firstComboTimesOut_remainingCombosStillRun() {
        List<RunResult> saved = new ArrayList<>();
        TestRunnerService service = serviceWithRepo(saved);

        // One chat model for the whole run: the first call times out, the rest succeed
        // (mirrors "combo N timed out, but the run kept going with the next combos").
        final boolean[] firstCall = {true};
        ChatModel flaky = new ChatModel() {
            @Override
            public ChatResponse chat(ChatRequest request) {
                if (firstCall[0]) {
                    firstCall[0] = false;
                    throw new TimeoutException("request timed out");
                }
                return ChatResponse.builder()
                        .aiMessage(AiMessage.from("ok"))
                        .tokenUsage(new TokenUsage(10, 5))
                        .build();
            }
        };

        RunResult a = Assertions.assertDoesNotThrow(() ->
                service.runOne(plainSuite(), tc(), Map.of("temperature", 0.1), 1, MODEL, flaky));
        RunResult b = service.runOne(plainSuite(), tc(), Map.of("temperature", 1.0), 2, MODEL, flaky);

        Assertions.assertEquals(2, saved.size());
        Assertions.assertEquals(EvaluationType.ERROR, a.getEvaluationType());
        Assertions.assertNull(a.getScore());
        // The second combo was NOT aborted by the first timeout.
        Assertions.assertEquals(EvaluationType.SKIPPED, b.getEvaluationType());
        Assertions.assertEquals("ok", b.getRawOutput());
        Assertions.assertEquals(10, b.getTokensIn());
        Assertions.assertEquals(5, b.getTokensOut());
        // Seed is persisted per run.
        Assertions.assertEquals(1, a.getSeed());
        Assertions.assertEquals(2, b.getSeed());
    }
}
