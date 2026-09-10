package dev.gnius.llmlab.service;

import dev.gnius.llmlab.domain.ExpectedOutputMode;
import dev.gnius.llmlab.domain.ParamSweep;
import dev.gnius.llmlab.domain.TestCase;
import dev.gnius.llmlab.domain.TestSuite;
import dev.gnius.llmlab.dto.ParamSweepDto;
import dev.gnius.llmlab.dto.SuiteCreateRequest;
import dev.gnius.llmlab.dto.SuiteResponse;
import dev.gnius.llmlab.dto.TestCaseDto;
import dev.gnius.llmlab.repository.ParamSweepRepository;
import dev.gnius.llmlab.repository.RunResultRepository;
import dev.gnius.llmlab.repository.TestCaseRepository;
import dev.gnius.llmlab.repository.TestSuiteRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.Set;

/**
 * Business logic for suite management: validation of the evaluation strategy and sweep
 * parameters, plus cascade create/replace of the nested test cases and parameter sweeps.
 */
@ApplicationScoped
public class TestSuiteService {

    /**
     * Sweepable, chat-level parameters (applied per call, not pre-set on the model).
     * All are part of the common OpenAI sampling set and are supported by both
     * OpenAI-compatible and Ollama providers via the generic {@code ChatRequestParameters}.
     */
    static final Set<String> VALID_PARAMS =
            Set.of("temperature", "topP", "topK", "frequencyPenalty", "presencePenalty", "maxTokens");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Inject
    TestSuiteRepository suiteRepo;
    @Inject
    TestCaseRepository caseRepo;
    @Inject
    ParamSweepRepository sweepRepo;
    @Inject
    RunResultRepository resultRepo;

    public List<SuiteResponse> list() {
        return suiteRepo.findAll().stream().map(this::toResponse).toList();
    }

    public SuiteResponse get(Long id) {
        return toResponse(requireSuite(id));
    }

    @Transactional
    public SuiteResponse create(SuiteCreateRequest req) {
        validate(req);
        TestSuite suite = new TestSuite(req.name());
        applyFields(suite, req);
        suiteRepo.save(suite);
        replaceChildren(suite.getId(), req);
        return toResponse(requireSuite(suite.getId()));
    }

    @Transactional
    public SuiteResponse update(Long id, SuiteCreateRequest req) {
        validate(req);
        TestSuite suite = requireSuite(id);
        applyFields(suite, req);
        suiteRepo.save(suite);
        replaceChildren(id, req);
        return toResponse(requireSuite(id));
    }

    @Transactional
    public void delete(Long id) {
        if (suiteRepo.findById(id).isEmpty()) {
            throw new SuiteNotFoundException(id);
        }
        for (TestCase tc : caseRepo.findAllBySuiteId(id)) {
            caseRepo.delete(tc.getId());
        }
        for (ParamSweep ps : sweepRepo.findAllBySuiteId(id)) {
            sweepRepo.delete(ps.getId());
        }
        suiteRepo.delete(id);
    }

    private TestSuite requireSuite(Long id) {
        return suiteRepo.findById(id).orElseThrow(() -> new SuiteNotFoundException(id));
    }

    private void applyFields(TestSuite suite, SuiteCreateRequest req) {
        suite.setName(req.name());
        suite.setDescription(req.description());
        suite.setExpectedOutput(req.expectedOutput());
        suite.setExpectedOutputMode(req.expectedOutputMode());
        suite.setJudgeModelId(req.judgeModelId());
        suite.setJudgePrompt(req.judgePrompt());
        suite.setJudgeTemperature(req.judgeTemperature());
        suite.setJudgeTopP(req.judgeTopP());
        suite.setJudgeSeed(req.judgeSeed());
        suite.setSeeds(serializeSeeds(req.seeds()));
    }

    /** Deletes existing children then inserts the request's (replacing the whole set). */
    private void replaceChildren(Long suiteId, SuiteCreateRequest req) {
        for (TestCase tc : caseRepo.findAllBySuiteId(suiteId)) {
            caseRepo.delete(tc.getId());
        }
        for (ParamSweep ps : sweepRepo.findAllBySuiteId(suiteId)) {
            sweepRepo.delete(ps.getId());
        }
        if (req.testCases() != null) {
            for (TestCaseDto tc : req.testCases()) {
                caseRepo.save(new TestCase(suiteId, tc.name(), tc.systemPrompt(), tc.userPrompt(), tc.sortOrder()));
            }
        }
        if (req.paramSweeps() != null) {
            for (ParamSweepDto ps : req.paramSweeps()) {
                sweepRepo.save(new ParamSweep(suiteId, ps.paramName(), ps.values()));
            }
        }
    }

    private SuiteResponse toResponse(TestSuite suite) {
        List<TestCaseDto> cases = caseRepo.findAllBySuiteId(suite.getId()).stream()
                .map(tc -> new TestCaseDto(tc.getId(), tc.getName(), tc.getSystemPrompt(), tc.getUserPrompt(), tc.getSortOrder()))
                .toList();
        List<ParamSweepDto> sweeps = sweepRepo.findAllBySuiteId(suite.getId()).stream()
                .map(ps -> new ParamSweepDto(ps.getParamName(), ps.getValues()))
                .toList();
        return new SuiteResponse(
                suite.getId(), suite.getName(), suite.getDescription(),
                suite.getExpectedOutput(), suite.getExpectedOutputMode(),
                suite.getJudgeModelId(), suite.getJudgePrompt(),
                suite.getJudgeTemperature(), suite.getJudgeTopP(), suite.getJudgeSeed(),
                suite.getCreatedAt(), suite.getUpdatedAt(),
                cases, sweeps, parseSeeds(suite.getSeeds()),
                resultRepo.findLatestRunAt(suite.getId()).orElse(null));
    }

    /** Serializes the seed list to a JSON array; null/empty stored as null ("generate one"). */
    private static String serializeSeeds(List<Integer> seeds) {
        if (seeds == null || seeds.isEmpty()) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(seeds);
        } catch (Exception e) {
            return null;
        }
    }

    /** Parses a stored seed JSON array; null/blank/invalid → empty list. */
    private static List<Integer> parseSeeds(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return MAPPER.readerForListOf(Integer.class).readValue(json);
        } catch (Exception e) {
            return List.of();
        }
    }

    private void validate(SuiteCreateRequest req) {
        if (req == null) {
            throw new ValidationException("Request body is required");
        }
        if (isBlank(req.name())) {
            throw new ValidationException("name is required");
        }
        if (req.expectedOutputMode() == null) {
            throw new ValidationException("expectedOutputMode is required");
        }
        if (req.expectedOutputMode() == ExpectedOutputMode.NONE) {
            if (req.judgeModelId() == null) {
                throw new ValidationException("judgeModelId is required when expectedOutputMode is NONE");
            }
        } else if (isBlank(req.expectedOutput())) {
            throw new ValidationException(
                    "expectedOutput is required when expectedOutputMode is " + req.expectedOutputMode());
        }
        if (req.testCases() != null) {
            for (TestCaseDto tc : req.testCases()) {
                if (isBlank(tc.name())) {
                    throw new ValidationException("testCase name is required");
                }
                if (isBlank(tc.userPrompt())) {
                    throw new ValidationException("testCase userPrompt is required");
                }
            }
        }
        if (req.paramSweeps() != null) {
            for (ParamSweepDto ps : req.paramSweeps()) {
                if (!VALID_PARAMS.contains(ps.paramName())) {
                    throw new ValidationException(
                            "paramName must be one of: " + VALID_PARAMS);
                }
                if (!isValidJsonArray(ps.values())) {
                    throw new ValidationException("values must be a valid JSON array");
                }
            }
        }
    }

    private static boolean isValidJsonArray(String values) {
        if (isBlank(values)) {
            return false;
        }
        try {
            JsonNode node = MAPPER.readTree(values);
            return node.isArray();
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
