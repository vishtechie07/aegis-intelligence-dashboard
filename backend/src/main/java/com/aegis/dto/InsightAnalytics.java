package com.aegis.dto;

import java.util.List;

public record InsightAnalytics(
        List<CategoryCount> categoriesLast7Days,
        List<SourceCount> sourcesLast7Days,
        List<ThreatHeatmapCell> highThreatByCompetitor
) {}
