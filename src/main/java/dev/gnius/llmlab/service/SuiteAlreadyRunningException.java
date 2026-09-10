package dev.gnius.llmlab.service;

/**
 * Thrown when a run is requested for a suite that already has a run in progress.
 * Mapped to HTTP 409 by {@link ApiExceptionMapper}.
 */
public class SuiteAlreadyRunningException extends ApiException {

    public SuiteAlreadyRunningException(Long suiteId) {
        super(409, "Suite " + suiteId + " is already running");
    }
}
