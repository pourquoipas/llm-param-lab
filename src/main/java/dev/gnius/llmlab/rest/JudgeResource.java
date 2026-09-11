package dev.gnius.llmlab.rest;

import dev.gnius.llmlab.dto.JudgeConfigRequest;
import dev.gnius.llmlab.dto.JudgeConfigResponse;
import dev.gnius.llmlab.service.JudgeService;
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

/** REST endpoints for the judge registry (anagrafica). */
@Path("/api/judges")
@Produces(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class JudgeResource {

    @Inject
    JudgeService service;

    @GET
    public List<JudgeConfigResponse> list() {
        return service.list();
    }

    @GET
    @Path("/{id}")
    public JudgeConfigResponse get(@PathParam("id") Long id) {
        return service.get(id);
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response create(JudgeConfigRequest request) {
        JudgeConfigResponse created = service.create(request);
        return Response.status(201).entity(created).build();
    }

    @PUT
    @Path("/{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    public JudgeConfigResponse update(@PathParam("id") Long id, JudgeConfigRequest request) {
        return service.update(id, request);
    }

    @DELETE
    @Path("/{id}")
    public Response delete(@PathParam("id") Long id) {
        service.delete(id);
        return Response.noContent().build();
    }
}
