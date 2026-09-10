package dev.gnius.llmlab.service;

import dev.gnius.llmlab.domain.RunResult;
import dev.gnius.llmlab.domain.TestCase;
import dev.gnius.llmlab.dto.RunSummaryResponse;
import dev.gnius.llmlab.dto.RunSummaryResponse.ComboSummary;
import dev.gnius.llmlab.dto.RunSummaryResponse.TestCaseSummary;
import dev.gnius.llmlab.repository.RunResultRepository;
import dev.gnius.llmlab.repository.TestCaseRepository;
import dev.gnius.llmlab.repository.TestSuiteRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Read-side of the run/results REST API: filtered result queries and the per-suite
 * summary (best parameter combination per test case).
 */
@ApplicationScoped
public class ResultService {

    private final RunResultRepository resultRepo;
    private final TestCaseRepository testCaseRepo;
    private final TestSuiteRepository suiteRepo;
    @PersistenceContext
    private EntityManager em;

    public ResultService(RunResultRepository resultRepo,
                         TestCaseRepository testCaseRepo,
                         TestSuiteRepository suiteRepo) {
        this.resultRepo = resultRepo;
        this.testCaseRepo = testCaseRepo;
        this.suiteRepo = suiteRepo;
    }

    /** Results filtered by suite and/or test case (both optional). */
    public List<RunResult> results(Long suiteId, Long testCaseId) {
        TypedQuery<RunResult> q = em.createQuery(
                "SELECT r FROM RunResult r"
                        + (suiteId != null ? " WHERE r.suiteId = :suiteId" : "")
                        + (testCaseId != null ? (suiteId != null ? " AND " : " WHERE ")
                                + "r.testCaseId = :testCaseId" : ""),
                RunResult.class);
        if (suiteId != null) {
            q.setParameter("suiteId", suiteId);
        }
        if (testCaseId != null) {
            q.setParameter("testCaseId", testCaseId);
        }
        return q.getResultList();
    }

    /**
     * Builds the per-suite summary grouped by seed: for each seed, for each test case,
     * the best combo and all combos. Comparisons are made at parity of seed.
     */
    public RunSummaryResponse summary(Long suiteId) {
        suiteRepo.findById(suiteId)
                .orElseThrow(() -> new SuiteNotFoundException(suiteId));
        List<RunResult> results = resultRepo.findBySuiteId(suiteId);
        Map<Integer, List<RunResult>> bySeed = results.stream()
                .collect(Collectors.groupingBy(r -> r.getSeed() == null ? 0 : r.getSeed()));

        List<RunSummaryResponse.SeedSummary> seedSummaries = bySeed.entrySet().stream()
                .map(e -> new RunSummaryResponse.SeedSummary(e.getKey(), buildCaseSummaries(e.getValue())))
                .sorted(Comparator.comparing(RunSummaryResponse.SeedSummary::seed,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        return new RunSummaryResponse(suiteId, seedSummaries);
    }

    /** Per-test-case summaries for a set of results (already scoped to one seed). */
    private List<TestCaseSummary> buildCaseSummaries(List<RunResult> results) {
        Map<Long, List<RunResult>> byCase = results.stream()
                .collect(Collectors.groupingBy(RunResult::getTestCaseId));
        List<TestCaseSummary> caseSummaries = new ArrayList<>();
        for (Map.Entry<Long, List<RunResult>> entry : byCase.entrySet()) {
            TestCase tc = testCaseRepo.findById(entry.getKey()).orElse(null);
            String name = tc != null ? tc.getName() : null;
            caseSummaries.add(toCaseSummary(entry.getKey(), name, entry.getValue()));
        }
        caseSummaries.sort(Comparator.comparing(
                s -> s.testCaseId() != null ? s.testCaseId() : 0L));
        return caseSummaries;
    }

    private TestCaseSummary toCaseSummary(Long testCaseId, String name, List<RunResult> results) {
        Map<String, List<RunResult>> byParams = results.stream()
                .collect(Collectors.groupingBy(r -> r.getParamsJson() == null ? "" : r.getParamsJson()));
        List<ComboSummary> combos = byParams.entrySet().stream()
                .map(e -> toCombo(e.getKey(), e.getValue()))
                .sorted(Comparator.comparing(ComboSummary::avgScore,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        ComboSummary best = combos.stream()
                .filter(c -> c.avgScore() != null)
                .max(Comparator.comparingDouble(ComboSummary::avgScore))
                .orElse(combos.isEmpty() ? null : combos.get(0));
        return new TestCaseSummary(testCaseId, name, best, combos);
    }

    private ComboSummary toCombo(String paramsJson, List<RunResult> results) {
        java.util.OptionalDouble avg = results.stream()
                .map(RunResult::getScore)
                .filter(java.util.Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .average();
        Double avgScore = avg.isPresent() ? avg.getAsDouble() : null;
        Long minLatency = results.stream().map(RunResult::getLatencyMs)
                .filter(java.util.Objects::nonNull).min(Long::compareTo).orElse(null);
        Long maxLatency = results.stream().map(RunResult::getLatencyMs)
                .filter(java.util.Objects::nonNull).max(Long::compareTo).orElse(null);
        return new ComboSummary(paramsJson, avgScore, minLatency, maxLatency, results.size());
    }
}
