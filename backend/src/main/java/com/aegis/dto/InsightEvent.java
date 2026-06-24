package com.aegis.dto;

import java.time.OffsetDateTime;

/**
 * SSE payload — mirrors the TypeScript Insight interface exactly.
 */
public record InsightEvent(
        Long id,
        Long newsId,
        String competitorName,
        String title,
        String sourceUrl,
        String sourceType,
        String agentName,
        String category,
        Integer threatLevel,
        String summary,
        String strategicAdvice,
        OffsetDateTime publishedAt,
        OffsetDateTime processedAt,
        String contentExcerpt,
        boolean ragAvailable,
        String clusterKey
) {}
