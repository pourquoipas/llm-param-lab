package dev.gnius.llmlab.report;

import com.lowagie.text.Cell;
import com.lowagie.text.Document;
import com.lowagie.text.Font;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Table;
import com.lowagie.text.pdf.PdfWriter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Renders a {@link ReportData} into an in-memory PDF (suite header, a best-combo
 * summary table, and a per-run results table). Uses OpenPDF's built-in base fonts,
 * so no font files are required. Pure function of the report data — no JPA.
 */
public final class PdfReport {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final float[] RESULT_WIDTHS = {0.14f, 0.06f, 0.16f, 0.06f, 0.05f,
            0.08f, 0.10f, 0.07f, 0.09f, 0.09f};

    private PdfReport() {
    }

    public static byte[] generate(ReportData data) {
        Document doc = new Document();
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            PdfWriter.getInstance(doc, out);
            doc.open();

            doc.add(new Paragraph("LLM Param Lab — Results Report", Fonts.heading()));
            doc.add(new Paragraph("Suite: " + str(data.suiteName()) + " (id " + data.suiteId() + ")", Fonts.body()));
            doc.add(new Paragraph("Generated: " + LocalDateTime.now().format(DATE_FMT), Fonts.body()));
            doc.add(new Paragraph(" "));

            addSummaryTable(doc, data);
            doc.add(new Paragraph(" "));
            addResultsTable(doc, data);

            doc.close();
            return out.toByteArray();
        } catch (Exception e) {
            IOException cause = e instanceof IOException ioe ? ioe : new IOException("failed to build pdf report", e);
            throw new UncheckedIOException("failed to build pdf report", cause);
        }
    }

    private static void addSummaryTable(Document doc, ReportData data) throws Exception {
        doc.add(new Paragraph("Best param combo (per seed)", Fonts.subheading()));
        if (data.summary() == null || data.summary().isEmpty()) {
            doc.add(new Paragraph("No summary data.", Fonts.body()));
            return;
        }
        float[] summaryWidths = {0.12f, 0.30f, 0.40f, 0.18f};
        Table table = new Table(summaryWidths.length);
        table.setWidths(summaryWidths);
        addHeader(table, "Seed", "Test Case", "Best Params", "Best Avg Score");
        for (ReportData.SummaryRow r : data.summary()) {
            table.addCell(bodyCell(r.seed()));
            table.addCell(bodyCell(r.testCase()));
            table.addCell(bodyCell(r.bestParams()));
            table.addCell(bodyCell(r.bestAvgScore()));
        }
        doc.add(table);
    }

    private static void addResultsTable(Document doc, ReportData data) throws Exception {
        doc.add(new Paragraph("Results", Fonts.subheading()));
        if (data.rows() == null || data.rows().isEmpty()) {
            doc.add(new Paragraph("No results.", Fonts.body()));
            return;
        }
        Table table = new Table(RESULT_WIDTHS.length);
        table.setWidths(RESULT_WIDTHS);
        addHeader(table, "Test Case", "Seed", "Params", "Score", "Pass",
                "Latency", "Tok In/Out", "Think", "In/Out t/s", "Eval");
        for (ReportData.ResultRow r : data.rows()) {
            table.addCell(bodyCell(r.testCase()));
            table.addCell(bodyCell(r.seed()));
            table.addCell(bodyCell(r.params()));
            table.addCell(bodyCell(r.score()));
            table.addCell(bodyCell(r.passed() == null ? null : (r.passed() ? "yes" : "no")));
            table.addCell(bodyCell(r.latencyMs()));
            table.addCell(bodyCell(r.tokensIn() + "/" + r.tokensOut()));
            table.addCell(bodyCell(r.reasoningTokens()));
            table.addCell(bodyCell(r.inputTps() + "/" + r.outputTps()));
            table.addCell(bodyCell(r.evaluationType()));
        }
        doc.add(table);
    }

    private static void addHeader(Table table, String... headers) {
        for (String h : headers) {
            table.addCell(new Cell(new Phrase(h, Fonts.headerCell())));
        }
    }

    private static Cell bodyCell(Object value) {
        return new Cell(new Phrase(str(value), Fonts.bodyCell()));
    }

    private static String str(Object v) {
        return v == null ? "" : String.valueOf(v);
    }

    /** Report fonts (OpenPDF embeds the Helvetica base fonts; no font files needed). */
    private static final class Fonts {
        static Font heading() { return new Font(Font.HELVETICA, 16, Font.BOLD); }
        static Font subheading() { return new Font(Font.HELVETICA, 12, Font.BOLD); }
        static Font body() { return new Font(Font.HELVETICA, 9, Font.NORMAL); }
        static Font headerCell() { return new Font(Font.HELVETICA, 8, Font.BOLD); }
        static Font bodyCell() { return new Font(Font.HELVETICA, 7, Font.NORMAL); }
    }
}
