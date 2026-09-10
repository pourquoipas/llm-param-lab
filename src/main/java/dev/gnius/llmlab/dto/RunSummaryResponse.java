package dev.gnius.llmlab.dto;

import java.util.List;

/**
 * Per-suite results summary, grouped by seed. Within each seed, for each test case:
 * the best parameter combination (highest average score) plus a table of every
 * combination with its average score and min/max latency. Comparisons are made at
 * parity of seed (group-by-seed), so runs from different seeds never mix.
 */
public record RunSummaryResponse(
        Long suiteId,
        List<SeedSummary> seeds) {

    /** All test-case summaries for one seed (a seed value, or null for legacy/unseeded runs). */
    public record SeedSummary(
            Integer seed,
            List<TestCaseSummary> testCases) {
    }

    /** Summary for one test case across all of its parameter combinations (within one seed). */
    public record TestCaseSummary(
            Long testCaseId,
            String testCaseName,
            ComboSummary bestCombo,
            List<ComboSummary> combos) {
    }

    /** Aggregated stats for one parameter combination (one {@code paramsJson}). */
    public record ComboSummary(
            String paramsJson,
            Double avgScore,
            Long minLatencyMs,
            Long maxLatencyMs,
            int runCount) {
    }
}
