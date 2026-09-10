package dev.gnius.llmlab.rest;

import dev.gnius.llmlab.domain.RunResult;
import dev.gnius.llmlab.dto.ResultDeleteRequest;
import dev.gnius.llmlab.dto.RunSummaryResponse;
import dev.gnius.llmlab.service.ReportService;
import dev.gnius.llmlab.service.ResultService;
import dev.gnius.llmlab.service.TestRunnerService;
import jakarta.annotation.security.PermitAll;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;

/**
 * Run + results REST API.
 *
 * <ul>
 *   <li>{@code POST /api/run-all} — run every suite</li>
 *   <li>{@code GET /api/results?suiteId=&testCaseId=} — filtered results</li>
 *   <li>{@code GET /api/results/summary?suiteId=} — per-suite summary</li>
 *   <li>{@code DELETE /api/results/{id}} — delete one result (204)</li>
 *   <li>{@code POST /api/results/delete} body {@code {ids:[…]}} — delete a set (204, 400 if empty)</li>
 *   <li>{@code DELETE /api/results?suiteId=[&testCaseId=]} — delete suite / case results (204, 400 if no suiteId)</li>
 *   <li>{@code DELETE /api/results/all} — delete every result (204)</li>
 * </ul>
 *
 * <p>The single-suite run endpoint lives in {@link TestSuiteResource}
 * ({@code POST /api/suites/{id}/run}) to keep suite-scoped routes together.
 */
@Path("/api")
@Produces(MediaType.APPLICATION_JSON)
@ApplicationScoped
@PermitAll
public class ResultResource {

    private final TestRunnerService runner;
    private final ResultService resultService;
    private final ReportService reportService;

    @Inject
    public ResultResource(TestRunnerService runner, ResultService resultService,
                          ReportService reportService) {
        this.runner = runner;
        this.resultService = resultService;
        this.reportService = reportService;
    }

    /** Runs every suite and returns all results. */
    @POST
    @Path("/run-all")
    public Response runAll() {
        List<RunResult> results = runner.runAll();
        return Response.ok(results).build();
    }

    /** Results filtered by suite and/or test case. */
    @GET
    @Path("/results")
    public List<RunResult> results(@QueryParam("suiteId") Long suiteId,
                                   @QueryParam("testCaseId") Long testCaseId) {
        return resultService.results(suiteId, testCaseId);
    }

    /** Per-suite summary: best parameter combination per test case. */
    @GET
    @Path("/results/summary")
    public RunSummaryResponse summary(@QueryParam("suiteId") Long suiteId) {
        return resultService.summary(suiteId);
    }

    /** Downloads the current suite's report as .xlsx. */
    @GET
    @Path("/results/export/xlsx")
    public Response exportXlsx(@QueryParam("suiteId") Long suiteId) {
        if (suiteId == null) {
            return Response.status(400).entity("suiteId is required").build();
        }
        if (!reportService.suiteExists(suiteId)) {
            return Response.status(404).entity("suite not found").build();
        }
        String filename = "report-" + safeName(reportService.suiteName(suiteId)) + ".xlsx";
        return attachment(filename,
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                reportService.xlsx(suiteId));
    }

    private static Response attachment(String filename, String contentType, byte[] body) {
        return Response.ok(body, contentType)
                .header("Content-Disposition", "attachment; filename=" + filename)
                .build();
    }

    private static String safeName(String name) {
        return name.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    /** Deletes a single result. Returns 204. */
    @DELETE
    @Path("/results/{id}")
    public Response deleteById(@PathParam("id") Long id) {
        resultService.deleteById(id);
        return Response.noContent().build();
    }

    /** Deletes the given set of results. Returns 204, or 400 when the id list is empty. */
    @POST
    @Path("/results/delete")
    public Response deleteByIds(ResultDeleteRequest request) {
        if (request == null || request.ids() == null || request.ids().isEmpty()) {
            return Response.status(400).entity("ids must not be empty").build();
        }
        resultService.deleteByIds(request.ids());
        return Response.noContent().build();
    }

    /**
     * Deletes a suite's results, optionally narrowed to one test case.
     * Returns 204, or 400 when {@code suiteId} is missing.
     */
    @DELETE
    @Path("/results")
    public Response deleteBySuite(@QueryParam("suiteId") Long suiteId,
                                  @QueryParam("testCaseId") Long testCaseId) {
        if (suiteId == null) {
            return Response.status(400).entity("suiteId is required").build();
        }
        resultService.deleteBySuite(suiteId, testCaseId);
        return Response.noContent().build();
    }

    /** Deletes every result. Returns 204. */
    @DELETE
    @Path("/results/all")
    public Response deleteAll() {
        resultService.deleteAllResults();
        return Response.noContent().build();
    }
}
