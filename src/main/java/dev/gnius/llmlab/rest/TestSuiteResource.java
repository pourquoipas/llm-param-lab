package dev.gnius.llmlab.rest;

import dev.gnius.llmlab.domain.RunResult;
import dev.gnius.llmlab.dto.SuiteCreateRequest;
import dev.gnius.llmlab.dto.SuiteResponse;
import dev.gnius.llmlab.service.TestRunnerService;
import dev.gnius.llmlab.service.TestSuiteService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;

/** REST endpoints for suite management (nested test cases + parameter sweeps). */
@Path("/api/suites")
@Produces(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class TestSuiteResource {

    @Inject
    TestSuiteService service;

    @Inject
    TestRunnerService runner;

    @GET
    public List<SuiteResponse> list() {
        return service.list();
    }

    /** Runs the suite synchronously and returns its results. */
    @POST
    @Path("/{id}/run")
    public List<RunResult> run(@PathParam("id") Long id) {
        return runner.runSuite(id);
    }

    @GET
    @Path("/{id}")
    public SuiteResponse get(@PathParam("id") Long id) {
        return service.get(id);
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response create(SuiteCreateRequest request) {
        SuiteResponse created = service.create(request);
        return Response.status(201).entity(created).build();
    }

    @PUT
    @Path("/{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    public SuiteResponse update(@PathParam("id") Long id, SuiteCreateRequest request) {
        return service.update(id, request);
    }

    @DELETE
    @Path("/{id}")
    public Response delete(@PathParam("id") Long id) {
        service.delete(id);
        return Response.noContent().build();
    }
}
