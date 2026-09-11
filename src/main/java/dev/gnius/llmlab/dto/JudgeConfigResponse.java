package dev.gnius.llmlab.dto;

import dev.gnius.llmlab.domain.Judge;

import java.time.LocalDateTime;

/** Read representation of a judge returned by the API. */
public record JudgeConfigResponse(
        Long id,
        String name,
        Long modelId,
        String prompt,
        Double temperature,
        Double topP,
        Integer seed,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static JudgeConfigResponse from(Judge j) {
        return new JudgeConfigResponse(
                j.getId(), j.getName(), j.getModelId(), j.getPrompt(),
                j.getTemperature(), j.getTopP(), j.getSeed(),
                j.getCreatedAt(), j.getUpdatedAt());
    }
}
