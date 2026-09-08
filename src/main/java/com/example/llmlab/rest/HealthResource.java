package com.example.llmlab.rest;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

@Path("/api")
public class HealthResource {

    @GET
    @Path("/ping")
    public String ping() {
        return "\"pong\"";
    }
}
