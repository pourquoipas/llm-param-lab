package dev.gnius.llmlab.report;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

/**
 * Renders a {@link ReportData} into an in-memory .xlsx workbook (two sheets:
 * "Summary" and "Results"). Pure function of the report data — no I/O beyond the
 * returned bytes, no JPA.
 */
public final class XlsxReport {

    private static final String[] RESULT_HEADERS = {
            "Test Case", "Params", "Seed", "Score", "Passed", "Latency (ms)",
            "Tokens In", "Tokens Out", "Thinking (tokens)", "In t/s", "Out t/s",
            "Eval Type", "Date", "Raw Output", "Score Reason"
    };

    private XlsxReport() {
    }

    public static byte[] generate(ReportData data) {
        try (Workbook wb = new XSSFWorkbook()) {
            writeSummary(wb.createSheet("Summary"), data);
            writeResults(wb.createSheet("Results"), data);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("failed to build xlsx report", e);
        }
    }

    private static void writeSummary(Sheet sheet, ReportData data) {
        Row header = sheet.createRow(0);
        setCell(header, 0, "Seed");
        setCell(header, 1, "Test Case");
        setCell(header, 2, "Best Params");
        setCell(header, 3, "Best Avg Score");
        List<ReportData.SummaryRow> rows = data.summary();
        for (int i = 0; i < rows.size(); i++) {
            ReportData.SummaryRow r = rows.get(i);
            Row row = sheet.createRow(i + 1);
            setCell(row, 0, r.seed());
            setCell(row, 1, r.testCase());
            setCell(row, 2, r.bestParams());
            setCell(row, 3, r.bestAvgScore());
        }
    }

    private static void writeResults(Sheet sheet, ReportData data) {
        Row header = sheet.createRow(0);
        for (int i = 0; i < RESULT_HEADERS.length; i++) {
            setCell(header, i, RESULT_HEADERS[i]);
        }
        List<ReportData.ResultRow> rows = data.rows();
        for (int i = 0; i < rows.size(); i++) {
            ReportData.ResultRow r = rows.get(i);
            Row row = sheet.createRow(i + 1);
            setCell(row, 0, r.testCase());
            setCell(row, 1, r.params());
            setCell(row, 2, r.seed());
            setCell(row, 3, r.score());
            setCell(row, 4, r.passed() == null ? null : (r.passed() ? "yes" : "no"));
            setCell(row, 5, r.latencyMs());
            setCell(row, 6, r.tokensIn());
            setCell(row, 7, r.tokensOut());
            setCell(row, 8, r.reasoningTokens());
            setCell(row, 9, r.inputTps());
            setCell(row, 10, r.outputTps());
            setCell(row, 11, r.evaluationType());
            setCell(row, 12, r.date());
            setCell(row, 13, r.rawOutput());
            setCell(row, 14, r.scoreReason());
        }
    }

    private static void setCell(Row row, int col, Object value) {
        Cell cell = row.createCell(col);
        if (value == null) {
            cell.setBlank();
        } else if (value instanceof Number n) {
            cell.setCellValue(n.doubleValue());
        } else {
            cell.setCellValue(String.valueOf(value));
        }
    }
}
