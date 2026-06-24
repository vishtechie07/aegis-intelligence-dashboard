package com.aegis.dto;

public record InsightStats(
        long totalArticles,
        long totalInsights,
        long filteredArticles,
        long todayHarvested,
        long todayAnalyzed,
        long todayFiltered,
        long highThreatCount
) {}
