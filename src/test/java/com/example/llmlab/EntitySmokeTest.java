package com.example.llmlab;

import com.example.llmlab.domain.EvaluationType;
import com.example.llmlab.domain.ExpectedOutputMode;
import com.example.llmlab.domain.ModelConfig;
import com.example.llmlab.domain.ModelProvider;
import com.example.llmlab.domain.ParamSweep;
import com.example.llmlab.domain.RunResult;
import com.example.llmlab.domain.TestCase;
import com.example.llmlab.domain.TestSuite;
import com.example.llmlab.repository.ModelConfigRepository;
import com.example.llmlab.repository.ParamSweepRepository;
import com.example.llmlab.repository.RunResultRepository;
import com.example.llmlab.repository.TestCaseRepository;
import com.example.llmlab.repository.TestSuiteRepository;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Smoke test: persist and read back one row of each entity, exercising the
 * entity-to-column mapping and the DB-level FK relationships.
 */
@QuarkusTest
class EntitySmokeTest {

    @Inject
    ModelConfigRepository modelConfigRepo;
    @Inject
    TestSuiteRepository suiteRepo;
    @Inject
    TestCaseRepository testCaseRepo;
    @Inject
    ParamSweepRepository sweepRepo;
    @Inject
    RunResultRepository resultRepo;

    @Test
    void persistAndReadEachEntity() {
        // 1. ModelConfig
        ModelConfig model = new ModelConfig("smoke-model", ModelProvider.OLLAMA,
                "http://localhost:11434", null, "llama3.1");
        model.setActive(true);
        modelConfigRepo.save(model);
        assertNotNull(model.getId());
        ModelConfig loadedModel = modelConfigRepo.findById(model.getId()).orElseThrow();
        assertEquals("smoke-model", loadedModel.getName());
        assertEquals(ModelProvider.OLLAMA, loadedModel.getProvider());
        assertTrue(loadedModel.isActive());
        assertTrue(modelConfigRepo.findActive().isPresent());

        // 2. TestSuite (judgeModelId FK -> model)
        TestSuite suite = new TestSuite("smoke-suite");
        suite.setExpectedOutput("hello");
        suite.setExpectedOutputMode(ExpectedOutputMode.EXACT);
        suite.setJudgeModelId(model.getId());
        suiteRepo.save(suite);
        assertNotNull(suite.getId());
        TestSuite loadedSuite = suiteRepo.findById(suite.getId()).orElseThrow();
        assertEquals("smoke-suite", loadedSuite.getName());
        assertEquals(ExpectedOutputMode.EXACT, loadedSuite.getExpectedOutputMode());
        assertEquals(model.getId(), loadedSuite.getJudgeModelId());
        assertNotNull(loadedSuite.getCreatedAt());
        assertNotNull(loadedSuite.getUpdatedAt());
        assertEquals(1, suiteRepo.findByJudgeModelId(model.getId()).size());

        // 3. TestCase (suiteId FK -> suite)
        TestCase testCase = new TestCase(suite.getId(), "smoke-case", "You are helpful.", "Say hi", 0);
        testCaseRepo.save(testCase);
        assertNotNull(testCase.getId());
        TestCase loadedCase = testCaseRepo.findById(testCase.getId()).orElseThrow();
        assertEquals("smoke-case", loadedCase.getName());
        assertEquals("You are helpful.", loadedCase.getSystemPrompt());
        assertEquals(1, testCaseRepo.findAllBySuiteId(suite.getId()).size());

        // 4. ParamSweep (suiteId FK -> suite)
        ParamSweep sweep = new ParamSweep(suite.getId(), "temperature", "[0.0, 0.5]");
        sweepRepo.save(sweep);
        assertNotNull(sweep.getId());
        ParamSweep loadedSweep = sweepRepo.findById(sweep.getId()).orElseThrow();
        assertEquals("temperature", loadedSweep.getParamName());
        assertEquals("[0.0, 0.5]", loadedSweep.getValues());
        assertEquals(1, sweepRepo.findAllBySuiteId(suite.getId()).size());

        // 5. RunResult (suiteId, testCaseId, modelConfigId FKs)
        RunResult result = new RunResult();
        result.setSuiteId(suite.getId());
        result.setTestCaseId(testCase.getId());
        result.setModelConfigId(model.getId());
        result.setParamsJson("{\"temperature\":0.5}");
        result.setRawOutput("hi");
        result.setLatencyMs(123L);
        result.setTokensIn(10);
        result.setTokensOut(5);
        result.setScore(1.0);
        result.setScoreReason("exact match");
        result.setEvaluationType(EvaluationType.EXACT_MATCH);
        result.setPassed(true);
        resultRepo.save(result);
        assertNotNull(result.getId());
        RunResult loadedResult = resultRepo.findById(result.getId()).orElseThrow();
        assertEquals(suite.getId(), loadedResult.getSuiteId());
        assertEquals(testCase.getId(), loadedResult.getTestCaseId());
        assertEquals(model.getId(), loadedResult.getModelConfigId());
        assertEquals(1.0, loadedResult.getScore());
        assertEquals(EvaluationType.EXACT_MATCH, loadedResult.getEvaluationType());
        assertTrue(loadedResult.getPassed());
        assertNotNull(loadedResult.getCreatedAt());
        assertEquals(1, resultRepo.findBySuiteId(suite.getId()).size());
        assertEquals(1, resultRepo.findByTestCaseId(testCase.getId()).size());
    }
}
