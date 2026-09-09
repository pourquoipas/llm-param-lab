package com.example.llmlab.rest;

import com.example.llmlab.domain.RunResult;
import com.example.llmlab.dto.RunSummaryResponse;
import com.example.llmlab.service.ResultService;
import com.example.llmlab.service.TestRunnerService;
import jakarta.annotation.security.PermitAll;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
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

    @Inject
    public ResultResource(TestRunnerService runner, ResultService resultService) {
        this.runner = runner;
        this.resultService = resultService;
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
}
