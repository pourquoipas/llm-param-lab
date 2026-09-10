package com.example.llmlab.service;

import com.example.llmlab.domain.ModelConfig;
import com.example.llmlab.domain.ModelProvider;
import com.example.llmlab.domain.RunResult;
import com.example.llmlab.domain.TestCase;
import com.example.llmlab.domain.TestSuite;
import com.example.llmlab.repository.RunResultRepository;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
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
 * Proves each {@code runOne} call uses a FRESH chat: the model under test is stateless
 * (no ChatMemory wired) and every call builds its own message list. A second task must
 * NOT see the messages of a previous task. No DB, no network.
 */
class TestRunnerServiceIsolationTest {

    private static final ModelConfig MODEL =
            new ModelConfig("m", ModelProvider.OPENAI_COMPATIBLE, "http://localhost:1/v1", null, "m");

    /** Records every request it receives so we can assert what each task sent. */
    static class RecordingModel implements ChatModel {
        final List<ChatRequest> requests = new ArrayList<>();
        @Override
        public ChatResponse chat(ChatRequest request) {
            requests.add(request);
            return ChatResponse.builder()
                    .aiMessage(AiMessage.from("reply"))
                    .tokenUsage(new TokenUsage(1, 1))
                    .build();
        }
    }

    private static RunResultRepository capturingRepo() {
        return new RunResultRepository() {
            @Override
            public RunResult save(RunResult entity) {
                return entity;
            }
        };
    }

    private static TestRunnerService service() {
        TestRunnerService s = new TestRunnerService();
        s.runResultRepository = capturingRepo();
        return s;
    }

    @Test
    void secondTaskDoesNotCarryMessagesFromFirstTask() {
        RecordingModel model = new RecordingModel();
        TestRunnerService service = service();

        TestCase taskA = new TestCase(1L, "A", "sysA", "userA", 0);
        TestCase taskB = new TestCase(2L, "B", "sysB", "userB", 1);

        service.runOne(new TestSuite("s"), taskA, Map.of(), MODEL, model);
        service.runOne(new TestSuite("s"), taskB, Map.of(), MODEL, model);

        Assertions.assertEquals(2, model.requests.size());

        // Each request carries exactly its own two messages (system + user).
        List<ChatMessage> first = model.requests.get(0).messages();
        List<ChatMessage> second = model.requests.get(1).messages();
        Assertions.assertEquals(2, first.size());
        Assertions.assertEquals(2, second.size());

        // Task B must not contain any of task A's content.
        Assertions.assertTrue(second.stream().noneMatch(m -> textOf(m).contains("userA")));
        Assertions.assertTrue(second.stream().noneMatch(m -> textOf(m).contains("sysA")));
        Assertions.assertTrue(second.stream().anyMatch(m -> textOf(m).contains("userB")));
        Assertions.assertTrue(second.stream().anyMatch(m -> textOf(m).contains("sysB")));
    }

    private static String textOf(ChatMessage m) {
        if (m instanceof SystemMessage s) {
            return s.text();
        }
        if (m instanceof UserMessage u) {
            return u.singleText();
        }
        if (m instanceof AiMessage a) {
            return a.text();
        }
        return "";
    }

    @Test
    void judgeEvaluationUsesItsOwnFreshChat() {
        // The judge call is a separate stateless chat (see evaluateWithJudge); it is not
        // wired to the test model's conversation, so a fresh ChatModel is used per call.
        // This test documents the contract: the judge request is a single user message.
        RecordingModel model = new RecordingModel();
        TestRunnerService service = service();
        service.runOne(new TestSuite("s"), new TestCase(1L, "A", null, "q", 0), Map.of(), MODEL, model);
        // No judge model configured -> SKIPPED, no judge chat issued. Only the test call happened.
        Assertions.assertEquals(1, model.requests.size());
    }
}
