package com.smartstudy.planning.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "storage.materials")
public record StorageProperties(
        String baseDir
) {
    public StorageProperties {
        if (baseDir == null) baseDir = "uploads/materials";
    }
}
