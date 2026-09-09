package com.example.llmlab.service;

/** A model name is already taken by another model. Maps to HTTP 409. */
public class ModelNameAlreadyExistsException extends ApiException {

    public ModelNameAlreadyExistsException(String name) {
        super(409, "Model name already exists: " + name);
    }
}
