package dev.gnius.llmlab.service;

import dev.gnius.llmlab.domain.ExpectedOutputMode;
import dev.gnius.llmlab.domain.ModelConfig;
import dev.gnius.llmlab.domain.ParamSweep;
import dev.gnius.llmlab.domain.TestCase;
import dev.gnius.llmlab.domain.TestSuite;
import dev.gnius.llmlab.repository.ModelConfigRepository;
import dev.gnius.llmlab.repository.ParamSweepRepository;
import dev.gnius.llmlab.repository.RunResultRepository;
import dev.gnius.llmlab.repository.TestCaseRepository;
import dev.gnius.llmlab.repository.TestSuiteRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/**
 * Admin operations: wipe the whole database and insert a live test suite
 * ({@code code reviewer}: 2 review personas × temperature [0.1, 1.0]) so an
 * agent can be tried end-to-end.
 */
@ApplicationScoped
public class AdminService {

    /** Name of the suite created by {@link #insertTestCase()}. */
    public static final String TEST_SUITE_NAME = "code reviewer";

    /** Shared user prompt: a non-thread-safe cache that must be reviewed. */
    private static final String USER_PROMPT = """
            Review this Java code for a distributed caching layer. It runs in a
            service with 10k req/s and multiple threads accessing the same cache
            concurrently.

            ```java
            public class SimpleCache {

                private final Map<String, CacheEntry> cache = new HashMap<>();
                private static final long TTL_MS = 60_000;

                public <T> T get(String key, Supplier<T> loader) {
                    CacheEntry entry = cache.get(key);

                    if (entry != null && !entry.isExpired()) {
                        return (T) entry.getValue();
                    }

                    T value = loader.get();
                    cache.put(key, new CacheEntry(value, System.currentTimeMillis() + TTL_MS));
                    return value;
                }

                public void invalidate(String key) {
                    cache.remove(key);
                }

                public int size() {
                    return cache.size();
                }

                static class CacheEntry {
                    private final Object value;
                    private final long expiresAt;

                    CacheEntry(Object value, long expiresAt) {
                        this.value = value;
                        this.expiresAt = expiresAt;
                    }

                    boolean isExpired() {
                        return System.currentTimeMillis() > expiresAt;
                    }

                    Object getValue() {
                        return value;
                    }
                }
            }
            ```
            """;

    /** Case 1 persona: operator-first mental simulation. */
    private static final String SYSTEM_THINKER = """
            You are a senior systems engineer reviewing code for a production service
            that handles 10k requests/second. You think in terms of: what breaks first
            under load, what corrupts data silently, what leaks memory over 30 days.

            YOUR APPROACH:
            1. First, put yourself in the shoes of the operator. What would they see
               in the monitoring dashboards if this code ran for a week? What alerts
               would fire? What would the post-mortem say?
            2. Then, think about the failure modes: what happens at t=0, at t=1ms,
               at t=1000ms, at t=30days? What happens with 1 thread, 10 threads,
               1000 threads? What happens when the input is null, empty, or max-size?
            3. From that mental simulation, extract the actual defects.

            SEVERITY RUBRIC (from the operator's perspective):
            - P0: An operator would page at 3am. Data is wrong or lost.
            - P1: An operator would notice in the morning. Results are subtly wrong.
            - P2: An operator would notice in a weekly review. Resources are wasted.

            RULES:
            - Only report actual defects, not improvements.
            - "Could be better" is not a bug. "Will cause X under condition Y" is.
            - If you cannot articulate the exact condition that triggers the bug,
              it goes in "ambiguous", not "bugs".
            - You are NOT the architect. You do not redesign. You find what's broken.

            OUTPUT FORMAT:
            Respond with ONLY a JSON object matching this schema exactly:
            {
              "intent": "<what this code does, one sentence>",
              "bugs": [
                {"severity": "P0|P1|P2", "line": <int>, "what": "<max 15 words>", "fix": "<max 20 words>"}
              ],
              "ambiguous": [
                {"line": <int>, "why": "<one sentence>"}
              ],
              "bug_count": <int>
            }

            No markdown fences. No preamble. No "Here is the analysis:". Just the JSON.
            """;

    /** Case 2 persona: strict 4-step audit protocol. */
    private static final String AUDIT_PROTOCOL = """
            You are a meticulous code auditor. You follow a strict protocol and never deviate from it.

            PROTOCOL (follow in exact order):

            STEP 1 — UNDERSTAND
            Read the code. State in one sentence what the code is supposed to do.
            Do not analyze yet. Just state the intent.

            STEP 2 — IDENTIFY
            Find ALL bugs. A bug is: code that will cause incorrect behavior, a crash,
            or a resource leak under normal (non-adversarial) usage.
            NOT a bug: style issues, missing tests, missing documentation,
            design preferences, or things that "could be better".

            For each bug, assign:
            - severity: P0 (crash/data corruption in production), P1 (incorrect result
              under specific conditions), P2 (performance or resource issue)
            - line: the line number where the bug manifests (1-indexed from the first
              line of the code block)
            - what: one sentence, max 15 words
            - fix: one sentence describing the minimal fix, max 20 words

            STEP 3 — AMBIGUOUS
            List any code that COULD be a bug but is plausibly intentional.
            For each: line number + one sentence on why it's ambiguous.
            Do NOT count these in your bug list.

            STEP 4 — OUTPUT
            Respond with ONLY a JSON object. No markdown, no explanation, no preamble.
            Schema:
            {
              "intent": "<one sentence from STEP 1>",
              "bugs": [
                {"severity": "P0|P1|P2", "line": <int>, "what": "<max 15 words>", "fix": "<max 20 words>"}
              ],
              "ambiguous": [
                {"line": <int>, "why": "<one sentence>"}
              ],
              "bug_count": <int, must equal length of bugs array>
            }

            HARD CONSTRAINTS:
            - Do NOT suggest refactoring, redesign, or architectural changes.
            - Do NOT add bugs that are not present in the code.
            - Do NOT flag style, naming, or "best practice" issues as bugs.
            - If you are unsure whether something is a bug, put it in "ambiguous", not "bugs".
            - Output must be valid JSON. No trailing commas. No comments.
            """;

    @Inject
    RunResultRepository resultRepo;
    @Inject
    TestCaseRepository caseRepo;
    @Inject
    ParamSweepRepository sweepRepo;
    @Inject
    TestSuiteRepository suiteRepo;
    @Inject
    ModelConfigRepository modelRepo;
    @Inject
    ModelConfigService modelService;

    /**
     * Wipes every table in FK order (run_result → test_case → param_sweep → test_suite →
     * model_config) so the database is left completely empty.
     */
    @Transactional
    public void clean() {
        resultRepo.deleteAll();
        caseRepo.deleteAll();
        sweepRepo.deleteAll();
        suiteRepo.deleteAll();
        modelRepo.deleteAll();
    }

    /**
     * Ensures the default model (from {@code .env}) exists and is active, then creates (or
     * replaces) the "code reviewer" suite: the same code-review user prompt × 2 system
     * personas (operator-first "system thinker", strict "Audit protocol") × temperature
     * [0.1, 1.0], judged by the default model. Re-runnable.
     */
    @Transactional
    public TestSuite insertTestCase() {
        ModelConfig model = modelService.ensureDefaultModel();
        modelService.activate(model.getId());

        TestSuite suite = suiteRepo.findAll().stream()
                .filter(s -> TEST_SUITE_NAME.equals(s.getName()))
                .findFirst()
                .orElseGet(() -> {
                    TestSuite s = new TestSuite(TEST_SUITE_NAME);
                    s.setDescription("review di codice");
                    s.setExpectedOutputMode(ExpectedOutputMode.NONE);
                    suiteRepo.save(s);
                    return s;
                });
        suite.setJudgeModelId(model.getId());
        suiteRepo.save(suite);

        // Full replace of children (no JPA cascade).
        for (TestCase tc : caseRepo.findAllBySuiteId(suite.getId())) {
            caseRepo.delete(tc.getId());
        }
        for (ParamSweep ps : sweepRepo.findAllBySuiteId(suite.getId())) {
            sweepRepo.delete(ps.getId());
        }
        caseRepo.save(new TestCase(suite.getId(), "system thinker", SYSTEM_THINKER, USER_PROMPT, 0));
        caseRepo.save(new TestCase(suite.getId(), "Audit protocol", AUDIT_PROTOCOL, USER_PROMPT, 1));
        sweepRepo.save(new ParamSweep(suite.getId(), "temperature", "[0.1, 1.0]"));
        return suite;
    }
}
