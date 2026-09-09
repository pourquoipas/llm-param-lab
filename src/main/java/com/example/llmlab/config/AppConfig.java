package com.example.llmlab.config;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Application-level configuration (the {@code app.*} block in application.yml).
 */
@ApplicationScoped
public class AppConfig {

    @ConfigProperty(name = "app.default-judge-prompt", defaultValue = "")
    String defaultJudgePrompt;

    public String getDefaultJudgePrompt() {
        return defaultJudgePrompt;
    }
}
