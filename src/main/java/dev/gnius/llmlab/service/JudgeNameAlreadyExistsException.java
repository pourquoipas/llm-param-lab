package dev.gnius.llmlab.service;

/** A judge name is already taken by another judge. Maps to HTTP 409. */
public class JudgeNameAlreadyExistsException extends ApiException {

    public JudgeNameAlreadyExistsException(String name) {
        super(409, "Judge name already exists: " + name);
    }
}
