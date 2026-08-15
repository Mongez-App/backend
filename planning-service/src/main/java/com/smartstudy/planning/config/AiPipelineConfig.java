package com.smartstudy.planning.config;

import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestClient;

@Configuration
@EnableScheduling
@EnableConfigurationProperties({
        GeminiProperties.class,
        OpenRouterProperties.class,
        QdrantProperties.class,
        StorageProperties.class,
        ProcessingProperties.class,
        ChatProperties.class
})
public class AiPipelineConfig {

    private static final Logger log = LoggerFactory.getLogger(AiPipelineConfig.class);

    @Bean
    public RestClient geminiRestClient(GeminiProperties props) {
        String apiKey = props.apiKey();

        // Fail fast — do not allow the application to start with a missing key
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "GEMINI_API_KEY is not configured. " +
                    "Set the GEMINI_API_KEY environment variable before starting the application. " +
                    "Current value is " + (apiKey == null ? "NULL" : "BLANK (empty string)") + ".");
        }

        // Log masked confirmation at startup
        String masked = apiKey.substring(0, Math.min(8, apiKey.length()))
                + "..."
                + apiKey.substring(Math.max(0, apiKey.length() - 4));
        log.info("Gemini REST client configured — API key: {} (length={})", masked, apiKey.length());

        return RestClient.builder()
                .baseUrl("https://generativelanguage.googleapis.com/v1beta")
                .defaultHeader("x-goog-api-key", apiKey)
                .defaultHeader("Content-Type", "application/json")
                .requestInterceptor(new GeminiRequestLoggingInterceptor())
                .build();
    }

    @Bean
    public RestClient openRouterRestClient(OpenRouterProperties props) {
        String baseUrl = props.baseUrl() != null && !props.baseUrl().isBlank()
                ? props.baseUrl()
                : "https://openrouter.ai/api/v1";

        return RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    @Bean
    public QdrantClient qdrantClient(QdrantProperties props) {
        log.info("Configuring Qdrant gRPC client — host: {}, port: {}", props.host(), props.port());
        return new QdrantClient(
                QdrantGrpcClient.newBuilder(props.host(), props.port(), false).build()
        );
    }
}

