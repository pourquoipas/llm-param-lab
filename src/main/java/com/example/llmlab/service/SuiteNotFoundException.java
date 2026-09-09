package com.example.llmlab.service;

/** A suite was referenced by an id that does not exist. Maps to HTTP 404. */
public class SuiteNotFoundException extends ApiException {

    public SuiteNotFoundException(Long id) {
        super(404, "Suite not found: " + id);
    }
}
