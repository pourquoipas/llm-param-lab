package dev.gnius.llmlab.report;

import java.util.List;

/**
 * Suite-wide export payload shared by the Excel and PDF report generators.
 * Decoupled from the persistence entities so the generators have no JPA dependency.
 */
public final class ReportData {

    private final String suiteName;
    private final Long suiteId;
    private final List<ResultRow> rows;
    private final List<SummaryRow> summary;

    public ReportData(String suiteName, Long suiteId, List<ResultRow> rows, List<SummaryRow> summary) {
        this.suiteName = suiteName;
        this.suiteId = suiteId;
        this.rows = rows;
        this.summary = summary;
    }

    public String suiteName() { return suiteName; }
    public Long suiteId() { return suiteId; }
    public List<ResultRow> rows() { return rows; }
    public List<SummaryRow> summary() { return summary; }

    /** One run result (a single test-case × params × seed run). */
    public record ResultRow(
            String testCase,
            String params,
            Integer seed,
            Double score,
            Boolean passed,
            Long latencyMs,
            Integer tokensIn,
            Integer tokensOut,
            Integer reasoningTokens,
            Double inputTps,
            Double outputTps,
            String evaluationType,
            String date,
            String rawOutput,
            String scoreReason) {
    }

    /** One best-combo summary line (per seed, per test case). */
    public record SummaryRow(
            Integer seed,
            String testCase,
            String bestParams,
            Double bestAvgScore) {
    }
}
