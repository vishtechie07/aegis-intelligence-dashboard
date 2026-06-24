package com.aegis.dto;

public record RelatedInsightBrief(
        Long newsId,
        Long insightId,
        String title,
        String sourceUrl,
        Integer threatLevel,
        String competitorName
) {}
