package com.smartstudy.planning.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "processing")
public record ProcessingProperties(
        long pollIntervalMs,
        long retryIntervalMs,
        int maxRetries
) {
    public ProcessingProperties {
        if (pollIntervalMs <= 0) pollIntervalMs = 5000;
        if (retryIntervalMs <= 0) retryIntervalMs = 1800000;
        if (maxRetries <= 0) maxRetries = 3;
    }
}
