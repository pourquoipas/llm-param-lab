package dev.gnius.llmlab.rest;

import dev.gnius.llmlab.dto.ModelConfigRequest;
import dev.gnius.llmlab.dto.ModelConfigResponse;
import dev.gnius.llmlab.service.ModelConfigService;
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

/** REST endpoints for model management. */
@Path("/api/models")
@Produces(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class ModelConfigResource {

    @Inject
    ModelConfigService service;

    @GET
    public List<ModelConfigResponse> list() {
        return service.list();
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response create(ModelConfigRequest request) {
        ModelConfigResponse created = service.create(request);
        return Response.status(201).entity(created).build();
    }

    @PUT
    @Path("/{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    public ModelConfigResponse update(@PathParam("id") Long id, ModelConfigRequest request) {
        return service.update(id, request);
    }

    @DELETE
    @Path("/{id}")
    public Response delete(@PathParam("id") Long id) {
        service.delete(id);
        return Response.noContent().build();
    }

    @POST
    @Path("/{id}/activate")
    public ModelConfigResponse activate(@PathParam("id") Long id) {
        return service.activate(id);
    }
}
