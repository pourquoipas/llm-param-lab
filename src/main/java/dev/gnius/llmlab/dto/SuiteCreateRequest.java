package dev.gnius.llmlab.dto;

import dev.gnius.llmlab.domain.ExpectedOutputMode;

import java.util.List;

/**
 * Create/update payload for a suite, including its nested test cases and parameter sweeps.
 * {@code seeds} is the optional seed-sweep list; empty/null means "generate one" at run time.
 */
public record SuiteCreateRequest(
        String name,
        String description,
        String expectedOutput,
        ExpectedOutputMode expectedOutputMode,
        Long judgeId,
        List<TestCaseDto> testCases,
        List<ParamSweepDto> paramSweeps,
        List<Integer> seeds) {
}
