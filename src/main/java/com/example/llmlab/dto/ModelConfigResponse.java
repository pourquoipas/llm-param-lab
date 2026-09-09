package com.example.llmlab.dto;

import com.example.llmlab.domain.ModelConfig;
import com.example.llmlab.domain.ModelProvider;

/** Read model representation returned by the API. */
public record ModelConfigResponse(
        Long id,
        String name,
        ModelProvider provider,
        String baseUrl,
        String apiKey,
        String modelName,
        boolean isActive) {

    public static ModelConfigResponse from(ModelConfig m) {
        return new ModelConfigResponse(
                m.getId(), m.getName(), m.getProvider(), m.getBaseUrl(),
                m.getApiKey(), m.getModelName(), m.isActive());
    }
}
