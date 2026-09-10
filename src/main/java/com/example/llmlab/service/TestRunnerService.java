package com.example.llmlab.service;

import com.example.llmlab.config.AppConfig;
import com.example.llmlab.config.ModelFactory;
import com.example.llmlab.domain.EvaluationType;
import com.example.llmlab.domain.ExpectedOutputMode;
import com.example.llmlab.domain.ModelConfig;
import com.example.llmlab.domain.ParamSweep;
import com.example.llmlab.domain.RunResult;
import com.example.llmlab.domain.TestCase;
import com.example.llmlab.domain.TestSuite;
import com.example.llmlab.dto.JudgeResponse;
import com.example.llmlab.repository.ModelConfigRepository;
import com.example.llmlab.repository.ParamSweepRepository;
import com.example.llmlab.repository.RunResultRepository;
import com.example.llmlab.repository.TestCaseRepository;
import com.example.llmlab.repository.TestSuiteRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Core run logic: executes a suite's test cases across the Cartesian product of its
 * parameter sweeps against the active model, then evaluates each response.
 * <p>
 * Sampling parameters are applied per call via {@link ChatRequestParameters} so the same
 * {@link ChatModel} instance is reused across all combinations.
 */
@ApplicationScoped
public class TestRunnerService {

    /** A judge verdict counts as a pass at or above this score. */
    private static final double JUDGE_PASS_THRESHOLD = 0.5;

    @Inject
    ModelConfigRepository modelConfigRepository;
    @Inject
    TestSuiteRepository testSuiteRepository;
    @Inject
    TestCaseRepository testCaseRepository;
    @Inject
    ParamSweepRepository paramSweepRepository;
    @Inject
    RunResultRepository runResultRepository;
    @Inject
    ModelFactory modelFactory;
    @Inject
    AppConfig appConfig;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ConcurrentHashMap<Long, Boolean> runningSuites = new ConcurrentHashMap<>();

    /** A single evaluation outcome: score (0.0–1.0 or null), reason, type, pass flag. */
    public record Evaluation(Double score, String reason, EvaluationType type, Boolean passed) {
    }

    /**
     * Runs one suite: every test case × every parameter combination, against the active model.
     *
     * @throws SuiteAlreadyRunningException if this suite is already running
     * @throws SuiteNotFoundException       if the suite does not exist
     * @throws NoActiveModelException       if no model is active
     */
    public List<RunResult> runSuite(Long suiteId) {
        if (runningSuites.putIfAbsent(suiteId, Boolean.TRUE) != null) {
            throw new SuiteAlreadyRunningException(suiteId);
        }
        try {
            TestSuite suite = testSuiteRepository.findById(suiteId)
                    .orElseThrow(() -> new SuiteNotFoundException(suiteId));
            List<TestCase> testCases = testCaseRepository.findAllBySuiteId(suiteId);
            List<ParamSweep> sweeps = paramSweepRepository.findAllBySuiteId(suiteId);
            ModelConfig model = modelConfigRepository.findActive()
                    .orElseThrow(NoActiveModelException::new);

            ChatModel chatModel = modelFactory.create(model);
            List<Map<String, Object>> combos = cartesianProduct(sweeps);

            List<RunResult> results = new ArrayList<>();
            for (TestCase testCase : testCases) {
                for (Map<String, Object> combo : combos) {
                    results.add(runOne(suite, testCase, combo, model, chatModel));
                }
            }
            return results;
        } finally {
            runningSuites.remove(suiteId);
        }
    }

    /** Runs every suite, returning all results in suite order. */
    public List<RunResult> runAll() {
        List<RunResult> all = new ArrayList<>();
        for (TestSuite suite : testSuiteRepository.findAll()) {
            all.addAll(runSuite(suite.getId()));
        }
        return all;
    }

    RunResult runOne(TestSuite suite, TestCase testCase, Map<String, Object> combo,
                     ModelConfig model, ChatModel chatModel) {
        List<ChatMessage> messages = new ArrayList<>();
        if (testCase.getSystemPrompt() != null && !testCase.getSystemPrompt().isBlank()) {
            messages.add(SystemMessage.from(testCase.getSystemPrompt()));
        }
        messages.add(UserMessage.from(testCase.getUserPrompt()));

        ChatRequest request = ChatRequest.builder()
                .messages(messages)
                .parameters(buildParameters(combo))
                .build();

        long start = System.nanoTime();
        String rawOutput = null;
        TokenUsage usage = null;
        long latencyMs;

        // 1) Call the model under test. A single failing combo must never abort the whole
        //    run (a local model can be slow and time out): record an ERROR result and
        //    let the next combo proceed.
        try {
            ChatResponse response = chatModel.chat(request);
            latencyMs = (System.nanoTime() - start) / 1_000_000L;
            rawOutput = response.aiMessage() != null ? response.aiMessage().text() : null;
            usage = response.tokenUsage();
        } catch (Exception e) {
            return saveResult(suite, testCase, combo, model, null, null,
                    (System.nanoTime() - start) / 1_000_000L,
                    null, "execution: " + errorMessage(e), EvaluationType.ERROR, false);
        }

        // 2) Evaluate. If evaluation itself fails (e.g. the judge model times out) we keep
        //    the captured output but still record the combo as ERROR so the run continues.
        Evaluation evaluation;
        try {
            evaluation = evaluate(suite, testCase, rawOutput);
        } catch (Exception e) {
            return saveResult(suite, testCase, combo, model, rawOutput, usage, latencyMs,
                    null, "evaluation: " + errorMessage(e), EvaluationType.ERROR, false);
        }

        return saveResult(suite, testCase, combo, model, rawOutput, usage, latencyMs,
                evaluation.score(), evaluation.reason(), evaluation.type(), evaluation.passed());
    }

    /** Builds and persists a single {@link RunResult} (shared by the success and error paths). */
    private RunResult saveResult(TestSuite suite, TestCase testCase, Map<String, Object> combo,
                                 ModelConfig model, String rawOutput, TokenUsage usage,
                                 long latencyMs, Double score, String scoreReason,
                                 EvaluationType evaluationType, Boolean passed) {
        RunResult result = new RunResult();
        result.setSuiteId(suite.getId());
        result.setTestCaseId(testCase.getId());
        result.setModelConfigId(model.getId());
        result.setParamsJson(toJson(combo));
        result.setRawOutput(rawOutput);
        result.setLatencyMs(latencyMs);
        if (usage != null) {
            result.setTokensIn(usage.inputTokenCount());
            result.setTokensOut(usage.outputTokenCount());
        }
        result.setScore(score);
        result.setScoreReason(scoreReason);
        result.setEvaluationType(evaluationType);
        result.setPassed(passed);
        return runResultRepository.save(result);
    }

    /** Compact, non-null message for a failed combo (e.g. {@code TimeoutException: request timed out}). */
    private static String errorMessage(Throwable e) {
        String msg = e.getMessage();
        return (msg != null && !msg.isBlank())
                ? e.getClass().getSimpleName() + ": " + msg
                : e.getClass().getSimpleName();
    }

    /**
     * Pure: builds the Cartesian product of all sweep values.
     * No sweeps → a single empty combo (one run per test case, no params).
     */
    public List<Map<String, Object>> cartesianProduct(List<ParamSweep> sweeps) {
        List<Map<String, Object>> result = new ArrayList<>();
        result.add(new LinkedHashMap<>());
        for (ParamSweep sweep : sweeps) {
            List<Object> values = parseValues(sweep.getValues());
            List<Map<String, Object>> next = new ArrayList<>();
            for (Map<String, Object> combo : result) {
                for (Object value : values) {
                    Map<String, Object> newCombo = new LinkedHashMap<>(combo);
                    newCombo.put(sweep.getParamName(), value);
                    next.add(newCombo);
                }
            }
            result = next;
        }
        return result;
    }

    /**
     * Picks the evaluation strategy.
     * expectedOutput set → expected-output match; else judgeModelId set → judge LLM call; else SKIPPED.
     */
    public Evaluation evaluate(TestSuite suite, TestCase testCase, String rawOutput) {
        if (suite.getExpectedOutput() != null) {
            return evaluateExpected(suite, rawOutput);
        }
        if (suite.getJudgeModelId() != null) {
            return evaluateWithJudge(suite, testCase, rawOutput);
        }
        return new Evaluation(null, null, EvaluationType.SKIPPED, null);
    }

    /** Pure: compares rawOutput against suite.expectedOutput using the suite's mode. */
    public Evaluation evaluateExpected(TestSuite suite, String rawOutput) {
        String expected = suite.getExpectedOutput();
        String output = rawOutput == null ? "" : rawOutput;
        ExpectedOutputMode mode = suite.getExpectedOutputMode() == null
                ? ExpectedOutputMode.EXACT : suite.getExpectedOutputMode();
        boolean pass;
        EvaluationType type;
        String reason;
        switch (mode) {
            case CONTAINS -> {
                pass = output.toLowerCase().contains(expected.toLowerCase());
                type = EvaluationType.CONTAINS;
                reason = pass ? "contains expected" : "does not contain expected";
            }
            case REGEX -> {
                pass = Pattern.compile(expected).matcher(output).find();
                type = EvaluationType.REGEX_MATCH;
                reason = pass ? "regex matched" : "regex did not match";
            }
            default -> { // EXACT (and NONE fallback)
                pass = output.trim().equals(expected.trim());
                type = EvaluationType.EXACT_MATCH;
                reason = pass ? "exact match" : "not an exact match";
            }
        }
        double score = pass ? 1.0 : 0.0;
        return new Evaluation(score, reason, type, pass);
    }

    /**
     * Judge-LLM evaluation: builds the judge prompt, calls the judge model at temperature 0,
     * and parses the JSON verdict. A parse failure yields score=null with a "judge parse error" reason.
     */
    Evaluation evaluateWithJudge(TestSuite suite, TestCase testCase, String rawOutput) {
        ModelConfig judgeModel = modelConfigRepository.findById(suite.getJudgeModelId())
                .orElseThrow(() -> new IllegalStateException("Judge model not found: " + suite.getJudgeModelId()));
        ChatModel judge = modelFactory.create(judgeModel);

        String prompt = buildJudgePrompt(suite, testCase, rawOutput);
        ChatRequest request = ChatRequest.builder()
                .messages(List.of(UserMessage.from(prompt)))
                .parameters(ChatRequestParameters.builder().temperature(0.0).build())
                .build();

        ChatResponse response = judge.chat(request);
        String judgeRaw = response.aiMessage() != null ? response.aiMessage().text() : null;
        JudgeResponse verdict = parseJudgeResponse(judgeRaw);
        if (verdict == null || verdict.score() == null) {
            return new Evaluation(null, "judge parse error: " + judgeRaw, EvaluationType.JUDGE_LLM, null);
        }
        double score = verdict.score();
        return new Evaluation(score, verdict.reason(), EvaluationType.JUDGE_LLM, score >= JUDGE_PASS_THRESHOLD);
    }

    /**
     * Pure: builds the judge prompt by substituting {{task}}, {{expected}}, {{response}}
     * into the suite's judge prompt (falling back to the configured default prompt).
     */
    public String buildJudgePrompt(TestSuite suite, TestCase testCase, String rawOutput) {
        String template = (suite.getJudgePrompt() != null && !suite.getJudgePrompt().isBlank())
                ? suite.getJudgePrompt()
                : appConfig.getDefaultJudgePrompt();
        String task = testCase.getUserPrompt() == null ? "" : testCase.getUserPrompt();
        String expected = suite.getExpectedOutput() == null ? "N/A" : suite.getExpectedOutput();
        String response = rawOutput == null ? "" : rawOutput;
        return template
                .replace("{{task}}", task)
                .replace("{{expected}}", expected)
                .replace("{{response}}", response);
    }

    /**
     * Pure: strips markdown code fences and isolates the JSON object from any surrounding text.
     */
    public String stripCodeFences(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.trim();
        int fenceStart = s.indexOf("```");
        if (fenceStart >= 0) {
            int contentStart = s.indexOf('\n', fenceStart);
            if (contentStart < 0) {
                contentStart = fenceStart + 3;
            }
            int fenceEnd = s.lastIndexOf("```");
            if (fenceEnd > contentStart) {
                s = s.substring(contentStart, fenceEnd);
            }
        }
        int objStart = s.indexOf('{');
        int objEnd = s.lastIndexOf('}');
        if (objStart >= 0 && objEnd > objStart) {
            s = s.substring(objStart, objEnd + 1);
        }
        return s.trim();
    }

    /**
     * Pure: leniently parses a judge reply into a {@link JudgeResponse}.
     * Returns null when no valid JSON verdict can be extracted.
     */
    public JudgeResponse parseJudgeResponse(String raw) {
        String cleaned = stripCodeFences(raw);
        if (cleaned == null || cleaned.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(cleaned, JudgeResponse.class);
        } catch (Exception e) {
            return null;
        }
    }

    private ChatRequestParameters buildParameters(Map<String, Object> combo) {
        var builder = ChatRequestParameters.builder();
        for (Map.Entry<String, Object> entry : combo.entrySet()) {
            switch (entry.getKey()) {
                case "temperature" -> builder.temperature(toDouble(entry.getValue()));
                case "topP" -> builder.topP(toDouble(entry.getValue()));
                case "maxTokens" -> builder.maxOutputTokens(toInt(entry.getValue()));
                default -> { /* unknown param name: ignore */ }
            }
        }
        return builder.build();
    }

    private List<Object> parseValues(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<Object>>() {
            });
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid param values JSON: " + json, e);
        }
    }

    private String toJson(Map<String, Object> combo) {
        try {
            return objectMapper.writeValueAsString(combo);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize params", e);
        }
    }

    private static Double toDouble(Object value) {
        return ((Number) value).doubleValue();
    }

    private static Integer toInt(Object value) {
        return ((Number) value).intValue();
    }
}
