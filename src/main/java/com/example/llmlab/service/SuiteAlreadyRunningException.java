package com.example.llmlab.service;

/**
 * Thrown when a run is requested for a suite that already has a run in progress.
 * Mapped to HTTP 409 in the REST layer (Step 9).
 */
public class SuiteAlreadyRunningException extends RuntimeException {

    public SuiteAlreadyRunningException(Long suiteId) {
        super("Suite " + suiteId + " is already running");
    }
}
