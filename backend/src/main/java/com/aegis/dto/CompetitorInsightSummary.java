package com.aegis.dto;

import java.util.List;

public record CompetitorInsightSummary(
        String competitorName,
        long totalInsights,
        long highThreatCount,
        List<CategoryCount> byCategory,
        List<SourceCount> bySource,
        List<InsightEvent> recentHighThreat
) {}
