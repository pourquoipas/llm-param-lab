package dev.gnius.llmlab;

import dev.gnius.llmlab.domain.EvaluationType;
import dev.gnius.llmlab.domain.ExpectedOutputMode;
import dev.gnius.llmlab.domain.Judge;
import dev.gnius.llmlab.domain.ParamSweep;
import dev.gnius.llmlab.domain.TestCase;
import dev.gnius.llmlab.domain.TestSuite;
import dev.gnius.llmlab.dto.JudgeResponse;
import dev.gnius.llmlab.service.TestRunnerService;
import dev.gnius.llmlab.service.TestRunnerService.Evaluation;
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
    void buildJudgePromptSubstitutesPlaceholders() {
        Judge judge = new Judge("all around");
        judge.setPrompt("T={{task}} E={{expected}} R={{response}}");
        TestCase tc = new TestCase(1L, "c", null, "do the thing", 0);
        String prompt = service.buildJudgePrompt(judge, tc, "the answer");
        Assertions.assertEquals("T=do the thing E=N/A R=the answer", prompt);
    }

    @Test
    void buildJudgePromptUsesExpectedWhenPresent() {
        Judge judge = new Judge("all around");
        judge.setPrompt("E={{expected}}");
        TestCase tc = new TestCase(1L, "c", null, "q", 0);
        String prompt = service.buildJudgePrompt(judge, tc, "out", "42");
        Assertions.assertEquals("E=42", prompt);
    }

    @Test
    void parseJudgeResponsePlainJson() {
        JudgeResponse r = service.parseJudgeResponse("{\"score\": 0.8, \"reason\": \"good\"}");
        Assertions.assertNotNull(r);
        Assertions.assertEquals(0.8, r.score().doubleValue(), 1e-9);
        Assertions.assertEquals("good", r.reason());
    }

    @Test
    void parseJudgeResponseFencedJson() {
        String raw = "Here is my verdict:\n```json\n{\"score\": 0.5, \"reason\": \"ok\"}\n```\nHope that helps!";
        JudgeResponse r = service.parseJudgeResponse(raw);
        Assertions.assertNotNull(r);
        Assertions.assertEquals(0.5, r.score().doubleValue(), 1e-9);
        Assertions.assertEquals("ok", r.reason());
    }

    @Test
    void parseJudgeResponseExtraTextAroundJson() {
        String raw = "Sure! {\"score\": 0.9, \"reason\": \"great\"} Let me know.";
        JudgeResponse r = service.parseJudgeResponse(raw);
        Assertions.assertNotNull(r);
        Assertions.assertEquals(0.9, r.score().doubleValue(), 1e-9);
    }

    @Test
    void parseJudgeResponseGarbageReturnsNull() {
        Assertions.assertNull(service.parseJudgeResponse("I cannot produce a score."));
        Assertions.assertNull(service.parseJudgeResponse(null));
        Assertions.assertNull(service.parseJudgeResponse(""));
    }

    private TestSuite suiteWithExpected(String expected, ExpectedOutputMode mode) {
        TestSuite suite = new TestSuite("s");
        suite.setExpectedOutput(expected);
        suite.setExpectedOutputMode(mode);
        return suite;
    }
}
