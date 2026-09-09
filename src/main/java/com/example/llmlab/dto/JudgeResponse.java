package com.example.llmlab.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Parsed judge-LLM verdict: a 0.0–1.0 score plus a one-sentence reason.
 * Deserialized leniently from the judge model's JSON reply; unknown fields are ignored.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record JudgeResponse(Double score, String reason) {
}
