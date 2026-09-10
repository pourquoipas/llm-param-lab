package dev.gnius.llmlab;

import dev.gnius.llmlab.domain.EvaluationType;
import dev.gnius.llmlab.domain.RunResult;
import dev.gnius.llmlab.domain.TestCase;
import dev.gnius.llmlab.domain.TestSuite;
import dev.gnius.llmlab.repository.RunResultRepository;
import dev.gnius.llmlab.repository.TestCaseRepository;
import dev.gnius.llmlab.repository.TestSuiteRepository;
import dev.gnius.llmlab.service.ResultService;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * Offline tests for result deletion (R1): the repository + service remove exactly the
 * intended rows across the four granularities (single, set, suite, all). Runs against
 * the in-memory H2 test DB; each test seeds its own suite so assertions are isolated.
 */
@QuarkusTest
class ResultDeletionTest {

    static int seq = 0;

    @Inject
    TestSuiteRepository suiteRepo;
    @Inject
    TestCaseRepository caseRepo;
    @Inject
    RunResultRepository resultRepo;
    @Inject
    ResultService resultService;

    /** Creates a suite with 2 test cases; returns [suiteId, tc1Id, tc2Id]. */
    private long[] seedSuite() {
        TestSuite suite = suiteRepo.save(new TestSuite("del-suite-" + (++seq)));
        long tc1 = caseRepo.save(new TestCase(suite.getId(), "tc1", "sys", "u", 0)).getId();
        long tc2 = caseRepo.save(new TestCase(suite.getId(), "tc2", "sys", "u", 1)).getId();
        return new long[]{suite.getId(), tc1, tc2};
    }

    private long seedRow(long suiteId, long testCaseId, String tag) {
        RunResult r = new RunResult();
        r.setSuiteId(suiteId);
        r.setTestCaseId(testCaseId);
        r.setParamsJson(tag);
        r.setScore(1.0);
        r.setEvaluationType(EvaluationType.EXACT_MATCH);
        return resultRepo.save(r).getId();
    }

    private int countForSuite(long suiteId) {
        return resultRepo.findBySuiteId(suiteId).size();
    }

    private boolean present(long id) {
        return resultRepo.findById(id).isPresent();
    }

    @Test
    void deleteByIdRemovesOnlyThatRow() {
        long[] ids = seedSuite();
        long keep1 = seedRow(ids[0], ids[1], "a");
        long keep2 = seedRow(ids[0], ids[1], "b");
        long victim = seedRow(ids[0], ids[2], "c");

        resultService.deleteById(victim);

        Assertions.assertFalse(present(victim), "victim row should be deleted");
        Assertions.assertTrue(present(keep1), "other rows should remain");
        Assertions.assertTrue(present(keep2), "other rows should remain");
        Assertions.assertEquals(2, countForSuite(ids[0]));
    }

    @Test
    void deleteByIdsRemovesExactlyTheGivenRows() {
        long[] ids = seedSuite();
        long a = seedRow(ids[0], ids[1], "a");
        long b = seedRow(ids[0], ids[1], "b");
        long c = seedRow(ids[0], ids[2], "c");

        resultService.deleteByIds(List.of(a, c));

        Assertions.assertFalse(present(a));
        Assertions.assertFalse(present(c));
        Assertions.assertTrue(present(b), "row not in the list should remain");
        Assertions.assertEquals(1, countForSuite(ids[0]));
    }

    @Test
    void deleteBySuiteRemovesOnlyThatSuite() {
        long[] suiteA = seedSuite();
        long[] suiteB = seedSuite();
        long a1 = seedRow(suiteA[0], suiteA[1], "a1");
        long a2 = seedRow(suiteA[0], suiteA[2], "a2");
        long b1 = seedRow(suiteB[0], suiteB[1], "b1");

        resultService.deleteBySuite(suiteA[0], null);

        Assertions.assertEquals(0, countForSuite(suiteA[0]), "suite A results should be gone");
        Assertions.assertFalse(present(a1));
        Assertions.assertFalse(present(a2));
        Assertions.assertTrue(present(b1), "other suite results should remain");
        Assertions.assertEquals(1, countForSuite(suiteB[0]));
    }

    @Test
    void deleteBySuiteAndTestCaseRemovesOnlyThatCase() {
        long[] ids = seedSuite();
        long inCase = seedRow(ids[0], ids[1], "in-case");
        long otherCase = seedRow(ids[0], ids[2], "other-case");

        resultService.deleteBySuite(ids[0], ids[1]);

        Assertions.assertFalse(present(inCase), "rows of the given case should be deleted");
        Assertions.assertTrue(present(otherCase), "rows of other cases should remain");
        Assertions.assertEquals(1, countForSuite(ids[0]));
    }

    @Test
    void deleteByIdsNoOpOnEmptyList() {
        long[] ids = seedSuite();
        long a = seedRow(ids[0], ids[1], "a");

        resultService.deleteByIds(List.of());

        Assertions.assertTrue(present(a), "empty list must not delete anything");
        Assertions.assertEquals(1, countForSuite(ids[0]));
    }

    @Test
    void deleteAllResultsRemovesEveryResult() {
        long[] ids = seedSuite();
        long a = seedRow(ids[0], ids[1], "a");
        long b = seedRow(ids[0], ids[2], "b");

        resultService.deleteAllResults();

        Assertions.assertFalse(present(a));
        Assertions.assertFalse(present(b));
        Assertions.assertEquals(0, countForSuite(ids[0]));
    }
}
