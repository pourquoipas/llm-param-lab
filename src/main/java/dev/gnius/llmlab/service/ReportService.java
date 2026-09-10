package dev.gnius.llmlab.service;

import dev.gnius.llmlab.domain.RunResult;
import dev.gnius.llmlab.domain.TestCase;
import dev.gnius.llmlab.domain.TestSuite;
import dev.gnius.llmlab.dto.RunSummaryResponse;
import dev.gnius.llmlab.report.ReportData;
import dev.gnius.llmlab.report.XlsxReport;
import dev.gnius.llmlab.repository.TestCaseRepository;
import dev.gnius.llmlab.repository.TestSuiteRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Builds the suite-wide {@link ReportData} and renders it to the export formats
 * (Excel now; PDF in R5). Read-only: never mutates results.
 */
@ApplicationScoped
public class ReportService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ResultService resultService;
    private final TestSuiteRepository suiteRepo;
    private final TestCaseRepository testCaseRepo;

    public ReportService(ResultService resultService,
                         TestSuiteRepository suiteRepo,
                         TestCaseRepository testCaseRepo) {
        this.resultService = resultService;
        this.suiteRepo = suiteRepo;
        this.testCaseRepo = testCaseRepo;
    }

    public boolean suiteExists(Long suiteId) {
        return suiteId != null && suiteRepo.findById(suiteId).isPresent();
    }

    public String suiteName(Long suiteId) {
        return suiteRepo.findById(suiteId).map(TestSuite::getName).orElse(String.valueOf(suiteId));
    }

    public byte[] xlsx(Long suiteId) {
        return XlsxReport.generate(buildReport(suiteId));
    }

    /** Assembles the report payload for a suite: suite name + all its results + best-combo summary. */
    public ReportData buildReport(Long suiteId) {
        Optional<TestSuite> suite = suiteRepo.findById(suiteId);
        String suiteName = suite.map(TestSuite::getName).orElse(String.valueOf(suiteId));

        Map<Long, String> caseNames = testCaseRepo.findAllBySuiteId(suiteId).stream()
                .collect(Collectors.toMap(TestCase::getId, TestCase::getName, (a, b) -> a));

        List<ReportData.ResultRow> rows = resultService.results(suiteId, null).stream()
                .map(r -> toRow(r, caseNames))
                .toList();

        RunSummaryResponse summary = resultService.summary(suiteId);
        List<ReportData.SummaryRow> summaryRows = new ArrayList<>();
        if (summary != null && summary.seeds() != null) {
            for (RunSummaryResponse.SeedSummary seed : summary.seeds()) {
                for (RunSummaryResponse.TestCaseSummary tc : seed.testCases()) {
                    String bestParams = tc.bestCombo() != null ? tc.bestCombo().paramsJson() : null;
                    Double bestScore = tc.bestCombo() != null ? tc.bestCombo().avgScore() : null;
                    summaryRows.add(new ReportData.SummaryRow(
                            seed.seed(), tc.testCaseName(), formatParams(bestParams), bestScore));
                }
            }
        }
        return new ReportData(suiteName, suiteId, rows, summaryRows);
    }

    private static ReportData.ResultRow toRow(RunResult r, Map<Long, String> caseNames) {
        return new ReportData.ResultRow(
                caseNames.getOrDefault(r.getTestCaseId(), String.valueOf(r.getTestCaseId())),
                formatParams(r.getParamsJson()),
                r.getSeed(),
                r.getScore(),
                r.getPassed(),
                r.getLatencyMs(),
                r.getTokensIn(),
                r.getTokensOut(),
                r.getReasoningTokens(),
                r.getInputTps(),
                r.getOutputTps(),
                r.getEvaluationType() != null ? r.getEvaluationType().name() : null,
                r.getCreatedAt() != null ? r.getCreatedAt().format(DATE_FMT) : null,
                r.getRawOutput(),
                r.getScoreReason());
    }

    /** {@code {"a":1,"b":2}} → {@code a=1, b=2} (the raw string if it is not a JSON object). */
    static String formatParams(String paramsJson) {
        if (paramsJson == null || paramsJson.isBlank()) {
            return null;
        }
        try {
            com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
            @SuppressWarnings("unchecked")
            Map<String, Object> map = om.readValue(paramsJson, Map.class);
            return map.entrySet().stream()
                    .map(e -> e.getKey() + "=" + e.getValue())
                    .collect(Collectors.joining(", "));
        } catch (Exception e) {
            return paramsJson;
        }
    }
}
