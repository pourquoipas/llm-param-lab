package com.example.llmlab.dto;

import java.util.List;

/**
 * Per-suite results summary. For each test case: the best parameter combination
 * (highest average score) plus a table of every combination with its average score
 * and min/max latency.
 */
public record RunSummaryResponse(
        Long suiteId,
        List<TestCaseSummary> testCases) {

    /** Summary for one test case across all of its parameter combinations. */
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
