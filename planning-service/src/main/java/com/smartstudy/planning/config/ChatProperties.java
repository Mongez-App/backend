package com.smartstudy.planning.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "chat")
public record ChatProperties(
        int historyContextSize,
        int qdrantSearchLimit,
        double qdrantScoreThreshold
) {
    public ChatProperties {
        if (historyContextSize <= 0) historyContextSize = 20;
        if (qdrantSearchLimit <= 0) qdrantSearchLimit = 5;
        if (qdrantScoreThreshold < 0) qdrantScoreThreshold = 0.5;
    }
}
