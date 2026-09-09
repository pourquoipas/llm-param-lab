package com.example.llmlab.rest;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.util.Map;

/**
 * Last-resort mapper: any exception not already handled (e.g. an unreachable LLM
 * during a run) is rendered as clean JSON instead of a framework error page.
 * Expected API errors are {@link com.example.llmlab.service.ApiException}s and are
 * handled by {@link ApiExceptionMapper} (more specific) before this one.
 *
 * <p>{@link WebApplicationException}s (e.g. {@code NotFoundException} for unmapped
 * routes) keep their own status code so 404s stay 404s; everything else is a 500.
 */
@Provider
public class GenericExceptionMapper implements ExceptionMapper<Exception> {

    @Override
    public Response toResponse(Exception ex) {
        if (ex instanceof WebApplicationException wae) {
            int status = wae.getResponse().getStatus();
            return Response.status(status)
                    .entity(Map.of("error", Response.Status.fromStatusCode(status).getReasonPhrase()))
                    .build();
        }
        return Response.status(500)
                .entity(Map.of("error", "Internal server error"))
                .build();
    }
}
