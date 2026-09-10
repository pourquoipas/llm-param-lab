package com.example.llmlab.service;

import com.example.llmlab.domain.ModelConfig;
import com.example.llmlab.domain.ModelProvider;
import com.example.llmlab.domain.RunResult;
import com.example.llmlab.domain.TestCase;
import com.example.llmlab.domain.TestSuite;
import com.example.llmlab.repository.RunResultRepository;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openai.OpenAiTokenUsage;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Offline tests for the per-run token/throughput stats (I4b): reasoning (thinking)
 * tokens from the OpenAI-compatible backend, and input/output tokens-per-second. No DB
 * and no network — the model is faked and the repository is an in-memory capture stub.
 */
class TestRunnerServiceTokenStatsTest {

    private static final ModelConfig MODEL =
            new ModelConfig("m", ModelProvider.OPENAI_COMPATIBLE, "http://localhost:1/v1", null, "m");

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

    private static TestCase tc() {
        return new TestCase(1L, "c", null, "hi", 0);
    }

    private static ChatModel returning(ChatResponse response) {
        return new ChatModel() {
            @Override
            public ChatResponse chat(ChatRequest request) {
                return response;
            }
        };
    }

    @Test
    void openAiUsagePersistsReasoningTokensAndThroughput() {
        List<RunResult> saved = new ArrayList<>();
        TestRunnerService service = serviceWithRepo(saved);
        ChatResponse response = ChatResponse.builder()
                .aiMessage(AiMessage.from("ok"))
                .tokenUsage(OpenAiTokenUsage.builder()
                        .inputTokenCount(100)
                        .outputTokenCount(40)
                        .outputTokensDetails(OpenAiTokenUsage.OutputTokensDetails.builder()
                                .reasoningTokens(25)
                                .build())
                        .build())
                .build();

        RunResult result = service.runOne(
                new TestSuite("s"), tc(), Map.of(), 1, MODEL, returning(response));

        Assertions.assertEquals(100, result.getTokensIn());
        Assertions.assertEquals(40, result.getTokensOut());
        // Thinking tokens come from the OpenAI-compatible backend.
        Assertions.assertEquals(25, result.getReasoningTokens());
    }

    @Test
    void baseUsageHasNoReasoningTokens() {
        List<RunResult> saved = new ArrayList<>();
        TestRunnerService service = serviceWithRepo(saved);
        ChatResponse response = ChatResponse.builder()
                .aiMessage(AiMessage.from("ok"))
                .tokenUsage(new TokenUsage(10, 5))
                .build();

        RunResult result = service.runOne(
                new TestSuite("s"), tc(), Map.of(), 1, MODEL, returning(response));

        Assertions.assertEquals(10, result.getTokensIn());
        Assertions.assertEquals(5, result.getTokensOut());
        // Ollama / base TokenUsage does not report reasoning tokens.
        Assertions.assertNull(result.getReasoningTokens());
    }

    @Test
    void throughputComputesTokensPerSecond() {
        Assertions.assertEquals(50.0, TestRunnerService.throughput(100, 2000), 1e-9);
        Assertions.assertNull(TestRunnerService.throughput(100, 0));
        Assertions.assertNull(TestRunnerService.throughput(null, 1000));
    }
}
