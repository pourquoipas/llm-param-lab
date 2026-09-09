package com.example.llmlab;

import com.example.llmlab.domain.EvaluationType;
import com.example.llmlab.domain.ExpectedOutputMode;
import com.example.llmlab.domain.ParamSweep;
import com.example.llmlab.domain.TestCase;
import com.example.llmlab.domain.TestSuite;
import com.example.llmlab.service.TestRunnerService;
import com.example.llmlab.service.TestRunnerService.Evaluation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

/**
 * Pure-logic tests for the runner core: Cartesian product + evaluators.
 * No DB, no model call — the service is instantiated directly.
 */
class TestRunnerServiceTest {

    private final TestRunnerService service = new TestRunnerService();

    @Test
    void cartesianProductTwoDimensions() {
        List<ParamSweep> sweeps = List.of(
                new ParamSweep(1L, "temperature", "[0.3, 0.5, 1.0]"),
                new ParamSweep(1L, "topP", "[0.8, 0.95]"));
        List<Map<String, Object>> combos = service.cartesianProduct(sweeps);
        Assertions.assertEquals(6, combos.size());
        for (Map<String, Object> combo : combos) {
            Assertions.assertTrue(combo.containsKey("temperature"));
            Assertions.assertTrue(combo.containsKey("topP"));
        }
    }

    @Test
    void cartesianProductNoSweeps() {
        List<Map<String, Object>> combos = service.cartesianProduct(List.of());
        Assertions.assertEquals(1, combos.size());
        Assertions.assertTrue(combos.get(0).isEmpty());
    }

    @Test
    void exactMatchPassAndFail() {
        TestSuite suite = suiteWithExpected("hello world", ExpectedOutputMode.EXACT);
        Assertions.assertEquals(1.0, service.evaluateExpected(suite, "  hello world  ").score().doubleValue());
        Assertions.assertEquals(0.0, service.evaluateExpected(suite, "goodbye").score().doubleValue());
    }

    @Test
    void containsMatchCaseInsensitive() {
        TestSuite suite = suiteWithExpected("Hello", ExpectedOutputMode.CONTAINS);
        Assertions.assertEquals(1.0, service.evaluateExpected(suite, "say hello there").score().doubleValue());
        Assertions.assertEquals(0.0, service.evaluateExpected(suite, "nothing here").score().doubleValue());
    }

    @Test
    void regexMatch() {
        TestSuite suite = suiteWithExpected("\\d+ items", ExpectedOutputMode.REGEX);
        Assertions.assertEquals(1.0, service.evaluateExpected(suite, "found 3 items today").score().doubleValue());
        Assertions.assertEquals(0.0, service.evaluateExpected(suite, "no numbers").score().doubleValue());
    }

    @Test
    void skippedWhenNoEvaluation() {
        TestSuite suite = new TestSuite("s");
        Evaluation e = service.evaluate(suite, new TestCase(1L, "c", null, "hi", 0), "out");
        Assertions.assertEquals(EvaluationType.SKIPPED, e.type());
        Assertions.assertNull(e.score());
    }

    @Test
    void judgePathReturnsJudgeType() {
        TestSuite suite = new TestSuite("s");
        suite.setJudgeModelId(99L);
        Evaluation e = service.evaluate(suite, new TestCase(1L, "c", null, "hi", 0), "out");
        Assertions.assertEquals(EvaluationType.JUDGE_LLM, e.type());
        Assertions.assertNull(e.score());
    }

    private TestSuite suiteWithExpected(String expected, ExpectedOutputMode mode) {
        TestSuite suite = new TestSuite("s");
        suite.setExpectedOutput(expected);
        suite.setExpectedOutputMode(mode);
        return suite;
    }
}
