package com.example.llmlab.config;

import com.example.llmlab.domain.ModelProvider;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Duration;
import java.util.Optional;

/**
 * LLM configuration read from the gitignored {@code .env} file (Quarkus maps the
 * {@code LLM_*} keys to {@code llm.*} properties). The {@code defaultValue}s let a
 * fresh clone (no {@code .env}) still boot with a sensible local default.
 * <p>
 * This single model is used for both execution (the active model) and evaluation
 * (the judge), per the project default.
 */
@ApplicationScoped
public class LlmConfig {

    @ConfigProperty(name = "llm.provider", defaultValue = "OPENAI_COMPATIBLE")
    String provider;

    @ConfigProperty(name = "llm.base.url", defaultValue = "http://localhost:11000/v1")
    String baseUrl;

    @ConfigProperty(name = "llm.model.name", defaultValue = "Qwen3.8-27B-UD-IQ3_S.gguf")
    String modelName;

    // Optional: Optional<String> is optional (no defaultValue) and tolerates an empty value.
    @ConfigProperty(name = "llm.api.key")
    Optional<String> apiKey;

    /**
     * Per-request timeout for the LLM HTTP calls. Generous by default because a local
     * model can be slow; override via {@code LLM_TIMEOUT} in {@code .env}
     * (ISO-8601, e.g. {@code PT15M}).
     */
    @ConfigProperty(name = "llm.timeout", defaultValue = "PT15M")
    Duration timeout;

    public ModelProvider provider() {
        return ModelProvider.valueOf(provider);
    }

    public String baseUrl() {
        return baseUrl;
    }

    public String modelName() {
        return modelName;
    }

    /** Per-request LLM timeout (default 15 minutes). */
    public Duration timeout() {
        return timeout;
    }

    /** API key, or null when unset/blank (local servers need no key). */
    public String apiKey() {
        return apiKey.map(String::trim).filter(s -> !s.isBlank()).orElse(null);
    }
}
