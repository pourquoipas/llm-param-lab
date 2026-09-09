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
     * replaces) the "Agent smoke test" suite: 2 test cases (distinct system + user prompts)
     * × temperature [0.5, 0.8], judged by the default model. Re-runnable.
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
                    s.setDescription("Minimal live test: 2 cases × temperature [0.5, 0.8].");
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
        caseRepo.save(new TestCase(suite.getId(), "helpful",
                "You are a helpful assistant.",
                "What is the capital of France?", 0));
        caseRepo.save(new TestCase(suite.getId(), "concise",
                "You are a concise assistant. Answer in one word.",
                "Name the largest planet in the solar system.", 1));
        sweepRepo.save(new ParamSweep(suite.getId(), "temperature", "[0.5, 0.8]"));
        return suite;
    }
}
