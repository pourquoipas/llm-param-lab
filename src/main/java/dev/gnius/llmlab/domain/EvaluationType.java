package dev.gnius.llmlab.domain;

/** How a {@link RunResult} was scored. */
public enum EvaluationType {
    EXACT_MATCH,
    CONTAINS,
    REGEX_MATCH,
    JUDGE_LLM,
    SKIPPED,
    /** The combo could not be executed/scored (e.g. LLM request timeout). */
    ERROR
}
