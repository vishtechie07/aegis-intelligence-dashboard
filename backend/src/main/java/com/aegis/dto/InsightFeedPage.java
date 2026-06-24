package com.aegis.dto;

import java.util.List;

public record InsightFeedPage(
        List<InsightEvent> items,
        long total,
        boolean hasMore
) {}
