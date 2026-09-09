package com.example.llmlab.seed;

import com.example.llmlab.domain.ExpectedOutputMode;
import com.example.llmlab.domain.ModelConfig;
import com.example.llmlab.domain.ParamSweep;
import com.example.llmlab.domain.TestCase;
import com.example.llmlab.domain.TestSuite;
import com.example.llmlab.repository.ModelConfigRepository;
import com.example.llmlab.repository.ParamSweepRepository;
import com.example.llmlab.repository.TestCaseRepository;
import com.example.llmlab.repository.TestSuiteRepository;
import com.example.llmlab.service.ModelConfigService;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/**
 * Seeds example data on first start (only when the relevant tables are empty, so it is
 * idempotent across restarts). Creates the default model (from {@code .env}) as the active
 * model and points the example suite's judge at it — the same model serves execution and
 * evaluation. Demonstrates the key feature: same user prompt, different system prompts,
 * different parameters — all in one run.
 */
@ApplicationScoped
public class SeedData {

    @Inject
    ModelConfigRepository modelRepo;
    @Inject
    TestSuiteRepository suiteRepo;
    @Inject
    TestCaseRepository caseRepo;
    @Inject
    ParamSweepRepository sweepRepo;
    @Inject
    ModelConfigService modelService;

    @Transactional
    void onStartup(@Observes StartupEvent event) {
        seedModels();
        seedSuite();
    }

    private void seedModels() {
        if (!modelRepo.findAll().isEmpty()) {
            return;
        }
        modelService.ensureDefaultModel();
    }

    private void seedSuite() {
        if (!suiteRepo.findAll().isEmpty()) {
            return;
        }
        Long judgeId = modelService.ensureDefaultModel().getId();

        TestSuite suite = new TestSuite("JSON extraction test");
        suite.setDescription("Same user prompt, different system prompts and parameters — all in one run.");
        suite.setExpectedOutputMode(ExpectedOutputMode.NONE);
        suite.setJudgeModelId(judgeId);
        suiteRepo.save(suite);
        Long id = suite.getId();

        caseRepo.save(new TestCase(id, "extract-name-simple",
                "You are a data extraction assistant. Always respond with valid JSON only.",
                "Extract the name from: 'Hi, I'm Marco Rossi'. Return JSON: {\"name\": \"...\"}", 0));
        caseRepo.save(new TestCase(id, "extract-name-agent",
                "You are an expert NLP agent. You extract structured data from unstructured text. "
                        + "Rules: 1) Always output valid JSON. 2) If a field is missing, use null. "
                        + "3) Do not add commentary.",
                "Extract the name from: 'Hi, I'm Marco Rossi'. Return JSON: {\"name\": \"...\"}", 1));
        caseRepo.save(new TestCase(id, "extract-multi",
                "You are a data extraction assistant. Always respond with valid JSON only.",
                "Extract names and emails from: 'Contact: Anna at a@b.com, Luca at l@c.com'. Return JSON array.", 2));

        sweepRepo.save(new ParamSweep(id, "temperature", "[0.0, 0.3, 0.7]"));
        sweepRepo.save(new ParamSweep(id, "topP", "[0.9, 0.95]"));
    }
}
