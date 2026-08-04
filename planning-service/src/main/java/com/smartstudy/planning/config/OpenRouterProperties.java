package com.smartstudy.planning.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for OpenRouter AI API.
 */
@ConfigurationProperties(prefix = "openrouter")
public record OpenRouterProperties(
        String apiKey,
        String baseUrl
) {
    public OpenRouterProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "https://openrouter.ai/api/v1";
        }
    }
}
