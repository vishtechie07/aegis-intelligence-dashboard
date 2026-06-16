package com.aegis.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record DeepDiveHistoryEntry(
        Long id,
        Long newsId,
        String question,
        String analysis,
        OffsetDateTime createdAt,
        List<DeepDiveSource> sources,
        boolean ragUsed) {}
