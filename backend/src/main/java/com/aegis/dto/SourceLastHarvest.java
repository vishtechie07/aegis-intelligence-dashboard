package com.aegis.dto;

import java.time.OffsetDateTime;

public record SourceLastHarvest(String sourceType, OffsetDateTime lastAt) {}
