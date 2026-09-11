package dev.gnius.llmlab.service;

/** A judge was referenced by an id that does not exist. Maps to HTTP 404. */
public class JudgeNotFoundException extends ApiException {

    public JudgeNotFoundException(Long id) {
        super(404, "Judge not found: " + id);
    }
}
