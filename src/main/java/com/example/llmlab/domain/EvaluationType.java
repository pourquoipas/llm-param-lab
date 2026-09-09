package com.example.llmlab.domain;

/** How a {@link RunResult} was scored. */
public enum EvaluationType {
    EXACT_MATCH,
    CONTAINS,
    REGEX_MATCH,
    JUDGE_LLM,
    SKIPPED
}
