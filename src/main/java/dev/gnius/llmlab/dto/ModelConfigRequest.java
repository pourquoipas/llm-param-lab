package dev.gnius.llmlab.dto;

import dev.gnius.llmlab.domain.ModelProvider;

/**
 * Create/update payload for a model. {@code apiKey} is optional (Ollama needs none).
 */
public record ModelConfigRequest(
        String name,
        ModelProvider provider,
        String baseUrl,
        String apiKey,
        String modelName) {
}
