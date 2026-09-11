package dev.gnius.llmlab.dto;

/**
 * Create/update payload for a judge. {@code modelId} null → use the active model at run time.
 * {@code prompt} null/blank → use the configured default judge prompt. {@code temperature}
 * null → 0.0 (deterministic).
 */
public record JudgeConfigRequest(
        String name,
        Long modelId,
        String prompt,
        Double temperature,
        Double topP,
        Integer seed) {
}
