package com.example.llmlab.dto;

/**
 * A single test case within a suite payload. {@code id} is read-only: populated on
 * read, ignored on create/update (the backend always creates fresh rows).
 */
public record TestCaseDto(
        Long id,
        String name,
        String systemPrompt,
        String userPrompt,
        int sortOrder) {
}
