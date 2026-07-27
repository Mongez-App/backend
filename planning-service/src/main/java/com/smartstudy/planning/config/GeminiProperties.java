package com.smartstudy.planning.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gemini")
public record GeminiProperties(
        String apiKey,
        EmbeddingConfig embedding,
        VisionConfig vision,
        ChatConfig chat
) {
    public record EmbeddingConfig(String model, int batchSize) {
        public EmbeddingConfig {
            if (model == null) model = "text-embedding-004";
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
            if (model == null) model = "gemini-2.0-flash";
            if (maxOutputTokens <= 0) maxOutputTokens = 2048;
            if (temperature < 0) temperature = 0.3;
        }
    }
}
