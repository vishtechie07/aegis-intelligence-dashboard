package com.aegis.dto;

import java.util.List;

public record RagRetrievalResult(
        List<DeepDiveSource> sources,
        String relatedContext,
        boolean ragUsed) {}
