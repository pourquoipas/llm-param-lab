package dev.gnius.llmlab;

import dev.gnius.llmlab.config.AppConfig;
import dev.gnius.llmlab.config.LlmConfig;
import dev.gnius.llmlab.config.ModelFactory;
import dev.gnius.llmlab.domain.ModelConfig;
import dev.gnius.llmlab.domain.ModelProvider;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Verifies ModelFactory builds a ChatModel per provider (object creation only, no API call)
 * and that AppConfig is injectable.
 */
@QuarkusTest
class ModelFactoryTest {

    @Inject
    ModelFactory modelFactory;

    @Inject
    AppConfig appConfig;

    @Inject
    LlmConfig llmConfig;

    @Test
    void llmTimeoutIsConfiguredAndGenerous() {
        // A local model can be slow: the default per-request timeout must be generous.
        Assertions.assertNotNull(llmConfig.timeout());
        Assertions.assertTrue(llmConfig.timeout().toMinutes() >= 5,
                "llm.timeout should default to a generous value (>= 5 min)");
    }

    @Test
    void buildsOllamaModel() {
        ModelConfig cfg = new ModelConfig("local-llama", ModelProvider.OLLAMA,
                "http://localhost:11434", null, "llama3.1");
        ChatModel model = modelFactory.create(cfg);
        Assertions.assertNotNull(model);
        Assertions.assertTrue(model instanceof OllamaChatModel);
    }

    @Test
    void buildsOpenAiCompatibleModelWithoutApiKey() {
        ModelConfig cfg = new ModelConfig("local-openai", ModelProvider.OPENAI_COMPATIBLE,
                "http://localhost:8080/v1", null, "some-model");
        ChatModel model = modelFactory.create(cfg);
        Assertions.assertNotNull(model);
        Assertions.assertTrue(model instanceof OpenAiChatModel);
    }

    @Test
    void appConfigInjectable() {
        Assertions.assertNotNull(appConfig.getDefaultJudgePrompt());
    }
}
