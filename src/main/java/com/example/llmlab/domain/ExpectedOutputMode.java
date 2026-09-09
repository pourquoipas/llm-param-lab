package com.example.llmlab.domain;

/** How a suite's {@code expectedOutput} is compared against a model response. */
public enum ExpectedOutputMode {
    EXACT,
    CONTAINS,
    REGEX,
    NONE
}
