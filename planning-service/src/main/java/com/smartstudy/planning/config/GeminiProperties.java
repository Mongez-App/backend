package com.smartstudy.planning.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "gemini")
public record GeminiProperties(
        @NotBlank(message = "gemini.api-key must not be blank. Set the GEMINI_API_KEY environment variable.")
        String apiKey,
        EmbeddingConfig embedding,
        VisionConfig vision,
        ChatConfig chat
) {
    public record EmbeddingConfig(String model, int batchSize) {
        public EmbeddingConfig {
            if (model == null) model = "gemini-embedding-001";
            if (batchSize <= 0) batchSize = 100;
        }
    }

    public record VisionConfig(String model) {
        public VisionConfig {
            if (model == null) model = "gemini-2.0-flash";
        }
    }

    public record ChatConfig(String model, int maxOutputTokens, double temperature) {
        public ChatConfig {
            // Keep in step with the default in application.yml — a retired ID here
            // 404s the whole Gemini chain and silently hands chat to OpenRouter.
            if (model == null) model = "gemini-3.5-flash-lite";
            if (maxOutputTokens <= 0) maxOutputTokens = 2048;
            if (temperature < 0) temperature = 0.3;
        }
    }
}

