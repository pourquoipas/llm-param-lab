package dev.gnius.llmlab.service;

import dev.gnius.llmlab.config.LlmConfig;
import dev.gnius.llmlab.domain.ModelConfig;
import dev.gnius.llmlab.dto.ModelConfigRequest;
import dev.gnius.llmlab.dto.ModelConfigResponse;
import dev.gnius.llmlab.repository.ModelConfigRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.List;

/**
 * Business logic for model management: validation, unique-name enforcement, and
 * the single-active-model invariant (activating one deactivates all others).
 */
@ApplicationScoped
public class ModelConfigService {

    /** Stable display name of the default model (from {@link LlmConfig}). */
    public static final String DEFAULT_MODEL_NAME = "default";

    @Inject
    ModelConfigRepository repository;

    @Inject
    LlmConfig llmConfig;

    public List<ModelConfigResponse> list() {
        return repository.findAll().stream().map(ModelConfigResponse::from).toList();
    }

    /**
     * Returns the default model (from {@link LlmConfig}), creating it and flagging it
     * active if it does not exist yet. Used by the startup seed and the admin
     * "insert test case" endpoint so both share one source of truth.
     */
    @Transactional
    public ModelConfig ensureDefaultModel() {
        return repository.findByName(DEFAULT_MODEL_NAME).orElseGet(() -> {
            ModelConfig model = new ModelConfig(
                    DEFAULT_MODEL_NAME, llmConfig.provider(), llmConfig.baseUrl(),
                    llmConfig.apiKey(), llmConfig.modelName());
            model.setActive(true);
            return repository.save(model);
        });
    }

    @Transactional
    public ModelConfigResponse create(ModelConfigRequest req) {
        validate(req);
        if (repository.findByName(req.name()).isPresent()) {
            throw new ModelNameAlreadyExistsException(req.name());
        }
        ModelConfig model = new ModelConfig(
                req.name(), req.provider(), req.baseUrl(), req.apiKey(), req.modelName());
        model.setReasoningEffort(normalizeEffort(req.reasoningEffort()));
        return ModelConfigResponse.from(repository.save(model));
    }

    @Transactional
    public ModelConfigResponse update(Long id, ModelConfigRequest req) {
        validate(req);
        ModelConfig model = repository.findById(id)
                .orElseThrow(() -> new ModelNotFoundException(id));
        repository.findByName(req.name()).ifPresent(existing -> {
            if (!existing.getId().equals(id)) {
                throw new ModelNameAlreadyExistsException(req.name());
            }
        });
        model.setName(req.name());
        model.setProvider(req.provider());
        model.setBaseUrl(req.baseUrl());
        model.setApiKey(req.apiKey());
        model.setModelName(req.modelName());
        model.setReasoningEffort(normalizeEffort(req.reasoningEffort()));
        return ModelConfigResponse.from(repository.save(model));
    }

    @Transactional
    public void delete(Long id) {
        if (repository.findById(id).isEmpty()) {
            throw new ModelNotFoundException(id);
        }
        repository.delete(id);
    }

    /** Deactivates every model, leaving no active model. */
    @Transactional
    public void deactivateAll() {
        for (ModelConfig model : repository.findAll()) {
            model.setActive(false);
            repository.save(model);
        }
    }

    /** Sets the given model active and deactivates every other model (atomic). */
    @Transactional
    public ModelConfigResponse activate(Long id) {
        ModelConfig target = repository.findById(id)
                .orElseThrow(() -> new ModelNotFoundException(id));
        for (ModelConfig model : repository.findAll()) {
            model.setActive(model.getId().equals(id));
            repository.save(model);
        }
        return ModelConfigResponse.from(target);
    }

    private void validate(ModelConfigRequest req) {
        if (req == null) {
            throw new ValidationException("Request body is required");
        }
        if (isBlank(req.name())) {
            throw new ValidationException("name is required");
        }
        if (req.provider() == null) {
            throw new ValidationException("provider is required");
        }
        if (isBlank(req.baseUrl())) {
            throw new ValidationException("baseUrl is required");
        }
        if (isBlank(req.modelName())) {
            throw new ValidationException("modelName is required");
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    /** Trims the effort value; blank → null (so an empty field means "not set"). */
    private static String normalizeEffort(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isBlank() ? null : t;
    }
}
