package com.example.llmlab.service;

import com.example.llmlab.domain.ExpectedOutputMode;
import com.example.llmlab.domain.ModelConfig;
import com.example.llmlab.domain.ParamSweep;
import com.example.llmlab.domain.TestCase;
import com.example.llmlab.domain.TestSuite;
import com.example.llmlab.repository.ModelConfigRepository;
import com.example.llmlab.repository.ParamSweepRepository;
import com.example.llmlab.repository.RunResultRepository;
import com.example.llmlab.repository.TestCaseRepository;
import com.example.llmlab.repository.TestSuiteRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/**
 * Admin operations: wipe the whole database and insert a minimal live test case
 * (2 test cases × temperature [0.5, 0.8]) so an agent can be tried end-to-end.
 */
@ApplicationScoped
public class AdminService {

    /** Name of the suite created by {@link #insertTestCase()}. */
    public static final String TEST_SUITE_NAME = "Agent smoke test";

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
     * replaces) the "Agent smoke test" suite: 3 challenging test cases (a logic trap the
     * model often gets wrong, a multi-step arithmetic case, and a strict-format case) ×
     * temperature [0.5, 0.8], judged by the default model. The difficulty gradient is meant
     * to produce varied judge scores (not all 1.0). Re-runnable.
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
                    s.setDescription("Challenging live test: 3 cases (trap, arithmetic, format) × temperature [0.5, 0.8].");
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
        // Hard: classic rate trap — the intuitive answer (100) is wrong; correct is 5.
        caseRepo.save(new TestCase(suite.getId(), "trap",
                "You are a careful problem solver. Reason step by step, then give the final answer.",
                "If it takes 5 machines 5 minutes to make 5 widgets, how long does it take 100 machines to make 100 widgets? Answer with only the number of minutes.",
                0));
        // Medium: multi-step arithmetic the model must compute correctly.
        caseRepo.save(new TestCase(suite.getId(), "arithmetic",
                "You are a precise calculator. Show your work step by step.",
                "What is 17 × 23? Show each step and give the final answer.",
                1));
        // Medium: strict output format the model tends to violate (extra text / markdown).
        caseRepo.save(new TestCase(suite.getId(), "format",
                "You are a data extraction assistant. Respond with ONLY a valid JSON object, no markdown, no extra text.",
                "Extract name and age from: 'My name is Alice and I am 30 years old.' Return JSON with keys 'name' (string) and 'age' (number).",
                2));
        sweepRepo.save(new ParamSweep(suite.getId(), "temperature", "[0.5, 0.8]"));
        return suite;
    }
}
