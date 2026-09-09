package com.example.llmlab.dto;

/** One parameter-sweep dimension within a suite payload. {@code values} is a JSON array string. */
public record ParamSweepDto(
        String paramName,
        String values) {
}
