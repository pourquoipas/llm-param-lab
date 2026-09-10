package dev.gnius.llmlab.service;

/**
 * Base for service-layer errors that map to an HTTP status.
 * Subclasses carry a specific status; a single {@code ApiExceptionMapper} renders them.
 */
public class ApiException extends RuntimeException {

    private final int status;

    public ApiException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int getStatus() {
        return status;
    }
}
