package dev.gnius.llmlab.service;

/** A request failed field-level validation. Maps to HTTP 400. */
public class ValidationException extends ApiException {

    public ValidationException(String message) {
        super(400, message);
    }
}
