package dev.gnius.llmlab.service;

import dev.gnius.llmlab.domain.Judge;
import dev.gnius.llmlab.dto.JudgeConfigRequest;
import dev.gnius.llmlab.dto.JudgeConfigResponse;
import dev.gnius.llmlab.repository.JudgeRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.List;

/**
 * Business logic for the judge registry (anagrafica): CRUD with unique-name enforcement and
 * the shared "all around" default judge that every suite falls back to.
 */
@ApplicationScoped
public class JudgeService {

    /** Stable display name of the shared generic judge. */
    public static final String DEFAULT_JUDGE_NAME = "all around";

    @Inject
    JudgeRepository repository;

    public List<JudgeConfigResponse> list() {
        return repository.findAll().stream().map(JudgeConfigResponse::from).toList();
    }

    public JudgeConfigResponse get(Long id) {
        return toResponse(requireJudge(id));
    }

    /**
     * Returns the shared "all around" judge, creating it (with a deterministic 0.0
     * temperature and no pinned model/prompt) if it does not exist yet. Idempotent.
     */
    @Transactional
    public Judge ensureDefaultJudge() {
        return repository.findByName(DEFAULT_JUDGE_NAME).orElseGet(() -> {
            Judge judge = new Judge(DEFAULT_JUDGE_NAME);
            judge.setTemperature(0.0);
            return repository.save(judge);
        });
    }

    @Transactional
    public JudgeConfigResponse create(JudgeConfigRequest req) {
        validate(req);
        if (repository.findByName(req.name()).isPresent()) {
            throw new JudgeNameAlreadyExistsException(req.name());
        }
        Judge judge = new Judge(req.name());
        applyFields(judge, req);
        return toResponse(repository.save(judge));
    }

    @Transactional
    public JudgeConfigResponse update(Long id, JudgeConfigRequest req) {
        validate(req);
        Judge judge = requireJudge(id);
        repository.findByName(req.name()).ifPresent(existing -> {
            if (!existing.getId().equals(id)) {
                throw new JudgeNameAlreadyExistsException(req.name());
            }
        });
        applyFields(judge, req);
        return toResponse(repository.save(judge));
    }

    @Transactional
    public void delete(Long id) {
        if (repository.findById(id).isEmpty()) {
            throw new JudgeNotFoundException(id);
        }
        repository.delete(id);
    }

    public boolean exists(Long id) {
        return id != null && repository.findById(id).isPresent();
    }

    private Judge requireJudge(Long id) {
        return repository.findById(id).orElseThrow(() -> new JudgeNotFoundException(id));
    }

    private JudgeConfigResponse toResponse(Judge judge) {
        return JudgeConfigResponse.from(judge);
    }

    private void applyFields(Judge judge, JudgeConfigRequest req) {
        judge.setName(req.name());
        judge.setModelId(req.modelId());
        judge.setPrompt(req.prompt());
        judge.setTemperature(req.temperature());
        judge.setTopP(req.topP());
        judge.setSeed(req.seed());
    }

    private void validate(JudgeConfigRequest req) {
        if (req == null) {
            throw new ValidationException("Request body is required");
        }
        if (isBlank(req.name())) {
            throw new ValidationException("name is required");
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
