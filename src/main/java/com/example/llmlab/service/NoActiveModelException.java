package com.example.llmlab.service;

/** No model is currently flagged active, so a run cannot proceed. Maps to HTTP 400. */
public class NoActiveModelException extends ApiException {

    public NoActiveModelException() {
        super(400, "No active model configured");
    }
}
