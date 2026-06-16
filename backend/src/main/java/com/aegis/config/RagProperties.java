package com.aegis.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "aegis.rag")
public record RagProperties(
        boolean enabled,
        boolean backfillOnStartup,
        int topK,
        int chunkSize,
        int chunkOverlap,
        int maxChunksPerArticle) {

    public RagProperties {
        if (topK <= 0) topK = 5;
        if (chunkSize <= 0) chunkSize = 1200;
        if (chunkOverlap < 0) chunkOverlap = 150;
        if (maxChunksPerArticle <= 0) maxChunksPerArticle = 20;
    }
}
