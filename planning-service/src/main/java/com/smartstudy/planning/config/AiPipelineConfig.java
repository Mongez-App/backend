package com.smartstudy.planning.config;

import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestClient;

@Configuration
@EnableScheduling
@EnableConfigurationProperties({
        GeminiProperties.class,
        QdrantProperties.class,
        StorageProperties.class,
        ProcessingProperties.class,
        ChatProperties.class
})
public class AiPipelineConfig {

    @Bean
    public RestClient geminiRestClient(GeminiProperties props) {
        return RestClient.builder()
                .baseUrl("https://generativelanguage.googleapis.com/v1beta")
                .defaultHeader("x-goog-api-key", props.apiKey())
                .build();
    }

    @Bean
    public QdrantClient qdrantClient(QdrantProperties props) {
        return new QdrantClient(
                QdrantGrpcClient.newBuilder(props.host(), props.port(), false).build()
        );
    }
}
