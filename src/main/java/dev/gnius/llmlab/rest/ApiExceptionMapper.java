package dev.gnius.llmlab.rest;

import dev.gnius.llmlab.service.ApiException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.util.Map;

/** Renders service-layer {@link ApiException}s as JSON error bodies with their HTTP status. */
@Provider
public class ApiExceptionMapper implements ExceptionMapper<ApiException> {

    @Override
    public Response toResponse(ApiException ex) {
        return Response.status(ex.getStatus())
                .entity(Map.of("error", ex.getMessage() == null ? "" : ex.getMessage()))
                .build();
    }
}
