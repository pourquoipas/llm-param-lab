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

    /** Stable display name of the code/technical-evaluation judge. */
    public static final String CODE_JUDGE_NAME = "code evaluation";

    /**
     * Code/technical-evaluation judge prompt. Uses the standard
     * {@code {{task}}}/{@code {{expected}}}/{@code {{response}}} placeholders and the
     * {@code {"score","reason"}} verdict the runner parses.
     */
    public static final String CODE_JUDGE_PROMPT = """
            You are an expert code reviewer evaluating an assistant's response to a programming task.
            Score the response from 0.0 to 1.0.

            Task given to the assistant:
            <task>{{task}}</task>

            Expected output (if any):
            <expected>{{expected}}</expected>

            Assistant's response:
            <response>{{response}}</response>

            Score criteria (code-focused):
            - Correctness: does it solve the task? Any bugs, race conditions, edge cases, wrong output?
            - Robustness: null/empty/exception handling, resource leaks, thread-safety.
            - Performance: algorithmic complexity, memory, obvious inefficiencies.
            - Readability: clear, idiomatic, no dead code.
            - 1.0 = correct, robust, efficient, clean
            - 0.7-0.9 = correct with minor issues
            - 0.4-0.6 = partially correct, notable bugs or gaps
            - 0.1-0.3 = mostly wrong or incomplete
            - 0.0 = nonsensical, unrunnable, or off-task

            Respond with ONLY a JSON object: {"score": <float>, "reason": "<one sentence>"}
            """;

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

    /**
     * Returns the code/technical-evaluation judge, creating it (deterministic 0.0 temperature,
     * code-focused prompt) if it does not exist yet. Idempotent by name.
     */
    @Transactional
    public Judge ensureCodeJudge() {
        return repository.findByName(CODE_JUDGE_NAME).orElseGet(() -> {
            Judge judge = new Judge(CODE_JUDGE_NAME);
            judge.setTemperature(0.0);
            judge.setPrompt(CODE_JUDGE_PROMPT);
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
