package dev.gnius.llmlab.rest;

import dev.gnius.llmlab.domain.TestSuite;
import dev.gnius.llmlab.dto.SuiteResponse;
import dev.gnius.llmlab.service.AdminService;
import dev.gnius.llmlab.service.TestSuiteService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/** Admin endpoints: wipe the database and insert a minimal live test case. */
@Path("/api/admin")
@Produces(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class AdminResource {

    @Inject
    AdminService service;

    @Inject
    TestSuiteService suiteService;

    /** Wipes every table (FK order). Returns 204 No Content. */
    @POST
    @Path("/clean")
    public Response clean() {
        service.clean();
        return Response.noContent().build();
    }

    /** Ensures the default model + creates the "Agent smoke test" suite. */
    @POST
    @Path("/test-case")
    public Response testCase() {
        TestSuite suite = service.insertTestCase();
        return Response.status(201).entity(suiteService.get(suite.getId())).build();
    }
}
