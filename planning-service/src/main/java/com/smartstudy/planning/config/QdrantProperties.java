package com.smartstudy.planning.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "qdrant")
public record QdrantProperties(
        String host,
        int port,
        String collectionName
) {
    public QdrantProperties {
        if (host == null) host = "localhost";
        if (port <= 0) port = 6334;
        if (collectionName == null) collectionName = "material_chunks";
    }
}
