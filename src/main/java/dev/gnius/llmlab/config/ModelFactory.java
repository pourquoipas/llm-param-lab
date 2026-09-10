package dev.gnius.llmlab.config;

import dev.gnius.llmlab.domain.ModelConfig;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Builds a langchain4j {@link ChatModel} for a {@link ModelConfig}.
 * <p>
 * Sampling parameters (temperature / topP / maxTokens) are deliberately NOT set here;
 * they are supplied per call via {@code ChatRequestParameters} (see TestRunnerService).
 * The per-request timeout is applied from {@link LlmConfig} (default 15 min).
 */
@ApplicationScoped
public class ModelFactory {

    @Inject
    LlmConfig llm;

    public ChatModel create(ModelConfig config) {
        return switch (config.getProvider()) {
            case OLLAMA -> OllamaChatModel.builder()
                    .baseUrl(config.getBaseUrl())
                    .modelName(config.getModelName())
                    .timeout(llm.timeout())
                    .build();
            case OPENAI_COMPATIBLE -> OpenAiChatModel.builder()
                    .baseUrl(config.getBaseUrl())
                    .apiKey(config.getApiKey())
                    .modelName(config.getModelName())
                    .timeout(llm.timeout())
                    .build();
        };
    }
}
