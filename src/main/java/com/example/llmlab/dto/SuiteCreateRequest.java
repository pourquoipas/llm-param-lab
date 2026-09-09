package com.example.llmlab.dto;

import com.example.llmlab.domain.ExpectedOutputMode;

import java.util.List;

/** Create/update payload for a suite, including its nested test cases and parameter sweeps. */
public record SuiteCreateRequest(
        String name,
        String description,
        String expectedOutput,
        ExpectedOutputMode expectedOutputMode,
        Long judgeModelId,
        String judgePrompt,
        List<TestCaseDto> testCases,
        List<ParamSweepDto> paramSweeps) {
}
