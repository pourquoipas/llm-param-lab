package dev.gnius.llmlab.service;

/** A model was referenced by an id that does not exist. Maps to HTTP 404. */
public class ModelNotFoundException extends ApiException {

    public ModelNotFoundException(Long id) {
        super(404, "Model not found: " + id);
    }
}
