package dev.gnius.llmlab.dto;

import dev.gnius.llmlab.domain.ModelProvider;

/**
 * Create/update payload for a model. {@code apiKey} is optional (Ollama needs none).
 * {@code reasoningEffort} is optional and only meaningful for {@code OPENAI_COMPATIBLE}.
 */
public record ModelConfigRequest(
        String name,
        ModelProvider provider,
        String baseUrl,
        String apiKey,
        String modelName,
        String reasoningEffort) {
}
