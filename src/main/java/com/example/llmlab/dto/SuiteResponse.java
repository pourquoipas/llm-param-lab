package com.example.llmlab.dto;

import com.example.llmlab.domain.ExpectedOutputMode;

import java.time.LocalDateTime;
import java.util.List;

/** Read representation of a suite with its nested test cases, sweeps, and latest-run timestamp. */
public record SuiteResponse(
        Long id,
        String name,
        String description,
        String expectedOutput,
        ExpectedOutputMode expectedOutputMode,
        Long judgeModelId,
        String judgePrompt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<TestCaseDto> testCases,
        List<ParamSweepDto> paramSweeps,
        LocalDateTime latestRunAt) {
}
