package com.example.llmlab.dto;

import com.example.llmlab.domain.ModelProvider;

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
