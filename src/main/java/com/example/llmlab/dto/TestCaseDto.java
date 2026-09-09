package com.example.llmlab.dto;

/** A single test case within a suite payload. */
public record TestCaseDto(
        String name,
        String systemPrompt,
        String userPrompt,
        int sortOrder) {
}
