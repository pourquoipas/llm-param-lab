package dev.gnius.llmlab.service;

import dev.gnius.llmlab.config.AppConfig;
import dev.gnius.llmlab.config.ModelFactory;
import dev.gnius.llmlab.domain.EvaluationType;
import dev.gnius.llmlab.domain.ExpectedOutputMode;
import dev.gnius.llmlab.domain.Judge;
import dev.gnius.llmlab.domain.ModelConfig;
import dev.gnius.llmlab.domain.ModelProvider;
import dev.gnius.llmlab.domain.ParamSweep;
import dev.gnius.llmlab.domain.RunResult;
import dev.gnius.llmlab.domain.TestCase;
import dev.gnius.llmlab.domain.TestSuite;
import dev.gnius.llmlab.dto.JudgeResponse;
import dev.gnius.llmlab.dto.RunOverrideRequest;
import dev.gnius.llmlab.repository.JudgeRepository;
import dev.gnius.llmlab.repository.ModelConfigRepository;
import dev.gnius.llmlab.repository.ParamSweepRepository;
import dev.gnius.llmlab.repository.RunResultRepository;
import dev.gnius.llmlab.repository.TestCaseRepository;
import dev.gnius.llmlab.repository.TestSuiteRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.DefaultChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.ollama.OllamaChatRequestParameters;
import dev.langchain4j.model.openai.OpenAiChatRequestParameters;
import dev.langchain4j.model.openai.OpenAiTokenUsage;
import dev.langchain4j.model.output.TokenUsage;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
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
    JudgeRepository judgeRepository;
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
     * Runs one suite: for each seed, every test case × every parameter combination, against
     * the active model. The seed list comes from the suite; when empty, one seed is generated
     * and used for all runs (and persisted on each result).
     *
     * @throws SuiteAlreadyRunningException if this suite is already running
     * @throws SuiteNotFoundException       if the suite does not exist
     * @throws NoActiveModelException       if no model is active
     */
    public List<RunResult> runSuite(Long suiteId) {
        return runSuite(suiteId, null);
    }

    /**
     * Runs the suite with an optional run-time override. When {@code override.judgeId()} is set,
     * that registry judge (404 if unknown) evaluates every result; otherwise the suite's own
     * evaluation strategy applies. {@code override.topK()} (nullable) is applied on top of the
     * judge's saved temperature/topP/seed.
     *
     * @throws SuiteAlreadyRunningException if this suite is already running
     * @throws SuiteNotFoundException       if the suite does not exist
     * @throws NoActiveModelException       if no model is active
     * @throws JudgeNotFoundException       if the override references an unknown judge
     */
    public List<RunResult> runSuite(Long suiteId, RunOverrideRequest override) {
        if (runningSuites.putIfAbsent(suiteId, Boolean.TRUE) != null) {
            throw new SuiteAlreadyRunningException(suiteId);
        }
        try {
            TestSuite suite = testSuiteRepository.findById(suiteId)
                    .orElseThrow(() -> new SuiteNotFoundException(suiteId));

            // Resolve the run-time override up front so a bad judge id fails (404) before any
            // other validation or model call.
            Judge overrideJudge = null;
            Integer overrideTopK = null;
            if (override != null) {
                overrideTopK = override.topK();
                if (override.judgeId() != null) {
                    overrideJudge = judgeRepository.findById(override.judgeId())
                            .orElseThrow(() -> new JudgeNotFoundException(override.judgeId()));
                }
            }

            List<TestCase> testCases = testCaseRepository.findAllBySuiteId(suiteId);
            List<ParamSweep> sweeps = paramSweepRepository.findAllBySuiteId(suiteId);
            ModelConfig model = modelConfigRepository.findActive()
                    .orElseThrow(NoActiveModelException::new);

            ChatModel chatModel = modelFactory.create(model);
            List<Map<String, Object>> combos = cartesianProduct(sweeps);
            List<Integer> seeds = resolveSeeds(suite);

            List<RunResult> results = new ArrayList<>();
            for (Integer seed : seeds) {
                for (TestCase testCase : testCases) {
                    for (Map<String, Object> combo : combos) {
                        results.add(runOne(suite, testCase, combo, seed, model, chatModel,
                                overrideJudge, overrideTopK));
                    }
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

    RunResult runOne(TestSuite suite, TestCase testCase, Map<String, Object> combo, Integer seed,
                     ModelConfig model, ChatModel chatModel) {
        return runOne(suite, testCase, combo, seed, model, chatModel, null, null);
    }

    /** Full form: {@code overrideJudge} (nullable) and {@code overrideTopK} (nullable) apply to
     *  this run's evaluation only. */
    RunResult runOne(TestSuite suite, TestCase testCase, Map<String, Object> combo, Integer seed,
                     ModelConfig model, ChatModel chatModel, Judge overrideJudge,
                     Integer overrideTopK) {
        List<ChatMessage> messages = new ArrayList<>();
        if (testCase.getSystemPrompt() != null && !testCase.getSystemPrompt().isBlank()) {
            messages.add(SystemMessage.from(testCase.getSystemPrompt()));
        }
        messages.add(UserMessage.from(testCase.getUserPrompt()));

        // The model's saved reasoning effort is only sent while the model still advertises the
        // capability. If the provider rejects the effort, the retry below disables it and
        // persists the flag so every later call omits it.
        String sentEffort = model.isReasoningCapability() ? model.getReasoningEffort() : null;
        ChatRequest request = ChatRequest.builder()
                .messages(messages)
                .parameters(buildParameters(combo, model.getProvider(), seed, sentEffort))
                .build();
        ChatRequest noEffortRequest = ChatRequest.builder()
                .messages(messages)
                .parameters(buildParameters(combo, model.getProvider(), seed, null))
                .build();

        long start = System.nanoTime();
        String rawOutput = null;
        TokenUsage usage = null;
        long latencyMs;

        // 1) Call the model under test. A single failing combo must never abort the whole
        //    run (a local model can be slow and time out): record an ERROR result and
        //    let the next combo proceed.
        try {
            ChatResponse response = chatWithReasoningRetry(chatModel, request, noEffortRequest,
                    model, sentEffort);
            latencyMs = (System.nanoTime() - start) / 1_000_000L;
            rawOutput = response.aiMessage() != null ? response.aiMessage().text() : null;
            usage = response.tokenUsage();
        } catch (Exception e) {
            return saveResult(suite, testCase, combo, seed, model, null, null,
                    (System.nanoTime() - start) / 1_000_000L,
                    null, "execution: " + errorMessage(e), EvaluationType.ERROR, false);
        }

        // 2) Evaluate. If evaluation itself fails (e.g. the judge model times out) we keep
        //    the captured output but still record the combo as ERROR so the run continues.
        Evaluation evaluation;
        try {
            evaluation = evaluate(suite, testCase, rawOutput, overrideJudge, overrideTopK);
        } catch (Exception e) {
            return saveResult(suite, testCase, combo, seed, model, rawOutput, usage, latencyMs,
                    null, "evaluation: " + errorMessage(e), EvaluationType.ERROR, false);
        }

        return saveResult(suite, testCase, combo, seed, model, rawOutput, usage, latencyMs,
                evaluation.score(), evaluation.reason(), evaluation.type(), evaluation.passed());
    }

    /** Builds and persists a single {@link RunResult} (shared by the success and error paths). */
    private RunResult saveResult(TestSuite suite, TestCase testCase, Map<String, Object> combo,
                                 Integer seed, ModelConfig model, String rawOutput, TokenUsage usage,
                                 long latencyMs, Double score, String scoreReason,
                                 EvaluationType evaluationType, Boolean passed) {
        RunResult result = new RunResult();
        result.setSuiteId(suite.getId());
        result.setTestCaseId(testCase.getId());
        result.setSeed(seed);
        result.setModelConfigId(model.getId());
        result.setParamsJson(toJson(combo));
        result.setRawOutput(rawOutput);
        result.setLatencyMs(latencyMs);
        if (usage != null) {
            Integer in = usage.inputTokenCount();
            Integer out = usage.outputTokenCount();
            result.setTokensIn(in);
            result.setTokensOut(out);
            // Thinking/reasoning tokens are only reported by the OpenAI-compatible backend
            // (Ollama returns the base TokenUsage); null otherwise.
            if (usage instanceof OpenAiTokenUsage oai) {
                var details = oai.outputTokensDetails();
                if (details != null) {
                    result.setReasoningTokens(details.reasoningTokens());
                }
            }
            result.setInputTps(throughput(in, latencyMs));
            result.setOutputTps(throughput(out, latencyMs));
        }
        result.setScore(score);
        result.setScoreReason(scoreReason);
        result.setEvaluationType(evaluationType);
        result.setPassed(passed);
        return runResultRepository.save(result);
    }

    /** Tokens/second over the total call latency (null when tokens or latency are unknown). */
    static Double throughput(Integer tokens, long latencyMs) {
        if (tokens == null || latencyMs <= 0) {
            return null;
        }
        return tokens / (latencyMs / 1000.0);
    }

    /** Compact, non-null message for a failed combo (e.g. {@code TimeoutException: request timed out}). */
    private static String errorMessage(Throwable e) {
        String msg = e.getMessage();
        return (msg != null && !msg.isBlank())
                ? e.getClass().getSimpleName() + ": " + msg
                : e.getClass().getSimpleName();
    }

    /**
     * Calls the model, and if the provider rejects a saved reasoning effort (the error message
     * mentions reasoning), disables the capability flag on {@code model} (persisted) and retries
     * once with {@code noEffortRequest}. Any other error, and a failing retry, propagate to the
     * caller. {@code sentEffort} is null when no effort was sent, so the retry never fires.
     */
    private ChatResponse chatWithReasoningRetry(ChatModel chatModel, ChatRequest request,
                                               ChatRequest noEffortRequest, ModelConfig model,
                                               String sentEffort) {
        try {
            return chatModel.chat(request);
        } catch (Exception e) {
            if (sentEffort != null && model.isReasoningCapability() && mentionsReasoning(e)) {
                model.setReasoningCapability(false);
                modelConfigRepository.save(model);
                return chatModel.chat(noEffortRequest);
            }
            throw e;
        }
    }

    /** Best-effort: true when the error message points at an unsupported reasoning parameter. */
    private static boolean mentionsReasoning(Throwable e) {
        String msg = e == null || e.getMessage() == null ? "" : e.getMessage().toLowerCase();
        return msg.contains("reasoning");
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
     * expectedOutput set → expected-output match; else judgeId set → judge LLM call; else SKIPPED.
     */
    public Evaluation evaluate(TestSuite suite, TestCase testCase, String rawOutput) {
        return evaluate(suite, testCase, rawOutput, null, null);
    }

    /**
     * Full form with a run-time override. When {@code overrideJudge} is set it wins over both the
     * expected-output match and the suite's own judge; {@code overrideTopK} (nullable) is applied
     * on top of the judge's saved parameters.
     */
    public Evaluation evaluate(TestSuite suite, TestCase testCase, String rawOutput,
                               Judge overrideJudge, Integer overrideTopK) {
        if (overrideJudge != null) {
            return evaluateWithJudge(overrideJudge, testCase, rawOutput, overrideTopK);
        }
        if (suite.getExpectedOutput() != null) {
            return evaluateExpected(suite, rawOutput);
        }
        if (suite.getJudgeId() != null) {
            Judge judge = judgeRepository.findById(suite.getJudgeId())
                    .orElseThrow(() -> new IllegalStateException("Judge not found: " + suite.getJudgeId()));
            return evaluateWithJudge(judge, testCase, rawOutput, null);
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
     * Judge-LLM evaluation: resolves the judge's model (pinned model, else the active model),
     * builds the judge prompt, and parses the JSON verdict. A parse failure yields score=null
     * with a "judge parse error" reason.
     */
    Evaluation evaluateWithJudge(Judge judge, TestCase testCase, String rawOutput) {
        return evaluateWithJudge(judge, testCase, rawOutput, null);
    }

    /** Full form: {@code topK} (nullable) overrides the judge's topK for this evaluation only. */
    Evaluation evaluateWithJudge(Judge judge, TestCase testCase, String rawOutput, Integer topK) {
        ModelConfig judgeModel = resolveJudgeModel(judge);
        ChatModel model = modelFactory.create(judgeModel);

        String prompt = buildJudgePrompt(judge, testCase, rawOutput);
        // Same reasoning-effort gating/retry as the model under test (J8), applied to the
        // judge's own model config.
        String sentEffort = judgeModel.isReasoningCapability() ? judgeModel.getReasoningEffort() : null;
        ChatRequest request = ChatRequest.builder()
                .messages(List.of(UserMessage.from(prompt)))
                .parameters(judgeParameters(judge, judgeModel.getProvider(), topK, sentEffort))
                .build();
        ChatRequest noEffortRequest = ChatRequest.builder()
                .messages(List.of(UserMessage.from(prompt)))
                .parameters(judgeParameters(judge, judgeModel.getProvider(), topK, null))
                .build();

        ChatResponse response = chatWithReasoningRetry(model, request, noEffortRequest,
                judgeModel, sentEffort);
        String judgeRaw = response.aiMessage() != null ? response.aiMessage().text() : null;
        JudgeResponse verdict = parseJudgeResponse(judgeRaw);
        if (verdict == null || verdict.score() == null) {
            return new Evaluation(null, "judge parse error: " + judgeRaw, EvaluationType.JUDGE_LLM, null);
        }
        double score = verdict.score();
        return new Evaluation(score, verdict.reason(), EvaluationType.JUDGE_LLM, score >= JUDGE_PASS_THRESHOLD);
    }

    /** A judge may pin a model; otherwise the suite's active model evaluates. */
    ModelConfig resolveJudgeModel(Judge judge) {
        if (judge.getModelId() != null) {
            return modelConfigRepository.findById(judge.getModelId())
                    .orElseThrow(() -> new IllegalStateException("Judge model not found: " + judge.getModelId()));
        }
        return modelConfigRepository.findActive()
                .orElseThrow(() -> new IllegalStateException("No active model for judge: " + judge.getId()));
    }

    /**
     * Pure: builds the judge prompt by substituting {{task}}, {{expected}}, {{response}}
     * into the judge's prompt (falling back to the configured default prompt).
     */
    public String buildJudgePrompt(Judge judge, TestCase testCase, String rawOutput) {
        return buildJudgePrompt(judge, testCase, rawOutput, null);
    }

    /** Overload with an explicit expected output (the suite's, when available). */
    public String buildJudgePrompt(Judge judge, TestCase testCase, String rawOutput, String expectedOutput) {
        String prompt = judge.getPrompt();
        String template = (prompt != null && !prompt.isBlank())
                ? prompt
                : appConfig.getDefaultJudgePrompt();
        String task = testCase.getUserPrompt() == null ? "" : testCase.getUserPrompt();
        String expected = expectedOutput == null ? "N/A" : expectedOutput;
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

    /**
     * Maps a combo of chat-level params to {@link ChatRequestParameters}. Uses the generic
     * builder so the same combo works for both OpenAI-compatible and Ollama models.
     * {@code seed} and {@code reasoningEffort} are provider-specific and handled separately
     * (see the seed sweep). Unknown names are ignored.
     */
    ChatRequestParameters buildParameters(Map<String, Object> combo) {
        return buildParameters(combo, ModelProvider.OPENAI_COMPATIBLE, null, null);
    }

    /** No-effort form: the model's own default applies (see the {@code reasoningEffort} overload). */
    ChatRequestParameters buildParameters(Map<String, Object> combo, ModelProvider provider, Integer seed) {
        return buildParameters(combo, provider, seed, null);
    }

    /**
     * Maps a combo of chat-level params (plus an optional provider-specific {@code seed} and an
     * optional OpenAI {@code reasoningEffort}) to {@link ChatRequestParameters}. Unknown names
     * are ignored; the effort is applied only for {@code OPENAI_COMPATIBLE} (see buildChatParams).
     */
    ChatRequestParameters buildParameters(Map<String, Object> combo, ModelProvider provider,
                                          Integer seed, String reasoningEffort) {
        Double temperature = null, topP = null, frequencyPenalty = null, presencePenalty = null;
        Integer topK = null, maxTokens = null;
        for (Map.Entry<String, Object> entry : combo.entrySet()) {
            switch (entry.getKey()) {
                case "temperature" -> temperature = toDouble(entry.getValue());
                case "topP" -> topP = toDouble(entry.getValue());
                case "topK" -> topK = toInt(entry.getValue());
                case "frequencyPenalty" -> frequencyPenalty = toDouble(entry.getValue());
                case "presencePenalty" -> presencePenalty = toDouble(entry.getValue());
                case "maxTokens" -> maxTokens = toInt(entry.getValue());
                default -> { /* unknown: ignored */ }
            }
        }
        return buildChatParams(provider, temperature, topP, topK,
                frequencyPenalty, presencePenalty, maxTokens, seed, reasoningEffort);
    }

    /**
     * Resolves the seed list for a run. When the suite has no seeds, a single random seed is
     * generated and used for all runs (it is persisted on each result).
     */
    List<Integer> resolveSeeds(TestSuite suite) {
        List<Integer> seeds = parseSeeds(suite.getSeeds());
        if (seeds == null || seeds.isEmpty()) {
            return List.of(ThreadLocalRandom.current().nextInt(0, Integer.MAX_VALUE));
        }
        return seeds;
    }

    /**
     * Builds the judge's sampling params from the judge's savable params.
     * Temperature defaults to 0.0 (deterministic) when not set. Uses the judge
     * model's provider so {@code seed} is applied provider-specifically.
     */
    ChatRequestParameters judgeParameters(Judge judge, ModelProvider provider) {
        return judgeParameters(judge, provider, null, null);
    }

    /** No-effort form: the judge model's own default applies (see the {@code reasoningEffort} overload). */
    ChatRequestParameters judgeParameters(Judge judge, ModelProvider provider, Integer topK) {
        return judgeParameters(judge, provider, topK, null);
    }

    /**
     * Full form: {@code topK} (nullable) overrides the judge's topK and {@code reasoningEffort}
     * (nullable) is the judge model's saved effort (OpenAI only). The judge entity has no savable
     * topK, so a null here means "no topK" (the model's own default applies).
     */
    ChatRequestParameters judgeParameters(Judge judge, ModelProvider provider,
                                         Integer topK, String reasoningEffort) {
        Double temp = judge.getTemperature() != null ? judge.getTemperature() : 0.0;
        return buildChatParams(provider, temp, judge.getTopP(), topK, null, null, null,
                judge.getSeed(), reasoningEffort);
    }

    /**
     * Provider-aware sampling params. OpenAI-compatible adds {@code reasoningEffort};
     * both OpenAI-compatible and Ollama support {@code seed}. Null values are omitted
     * so the model's own default applies.
     */
    private ChatRequestParameters buildChatParams(ModelProvider provider,
                                                  Double temperature, Double topP, Integer topK,
                                                  Double frequencyPenalty, Double presencePenalty,
                                                  Integer maxTokens, Integer seed, String reasoningEffort) {
        if (provider == ModelProvider.OPENAI_COMPATIBLE) {
            OpenAiChatRequestParameters.Builder b = OpenAiChatRequestParameters.builder();
            applyCommon(b, temperature, topP, topK, frequencyPenalty, presencePenalty, maxTokens);
            if (seed != null) {
                b.seed(seed);
            }
            if (reasoningEffort != null) {
                b.reasoningEffort(reasoningEffort);
            }
            return b.build();
        }
        OllamaChatRequestParameters.Builder b = OllamaChatRequestParameters.builder();
        applyCommon(b, temperature, topP, topK, frequencyPenalty, presencePenalty, maxTokens);
        if (seed != null) {
            b.seed(seed);
        }
        return b.build();
    }

    private static <T extends DefaultChatRequestParameters.Builder<T>> void applyCommon(
            T b, Double temperature, Double topP, Integer topK,
            Double frequencyPenalty, Double presencePenalty, Integer maxTokens) {
        if (temperature != null) {
            b.temperature(temperature);
        }
        if (topP != null) {
            b.topP(topP);
        }
        if (topK != null) {
            b.topK(topK);
        }
        if (frequencyPenalty != null) {
            b.frequencyPenalty(frequencyPenalty);
        }
        if (presencePenalty != null) {
            b.presencePenalty(presencePenalty);
        }
        if (maxTokens != null) {
            b.maxOutputTokens(maxTokens);
        }
    }

    private List<Object> parseValues(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<Object>>() {
            });
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid param values JSON: " + json, e);
        }
    }

    /** Parses the suite's stored seed JSON array; null/blank/invalid → empty list. */
    private List<Integer> parseSeeds(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<Integer>>() {
            });
        } catch (Exception e) {
            return List.of();
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
