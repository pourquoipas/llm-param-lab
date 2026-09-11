package dev.gnius.llmlab.dto;

/**
 * Optional body for {@code POST /api/suites/{id}/run}. Both fields are nullable:
 * {@code judgeId} null → evaluate with the suite's own judge; {@code topK} null → the
 * judge's saved parameters apply unchanged. {@code judgeId} set → a different registry
 * judge is used for this run only (404 if the id is unknown).
 */
public record RunOverrideRequest(
        Long judgeId,
        Integer topK) {
}
