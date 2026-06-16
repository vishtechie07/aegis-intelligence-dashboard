package com.aegis.dto;

import java.util.List;

public record DeepDiveResponse(
        String analysis,
        List<DeepDiveSource> sources,
        boolean ragUsed) {}
