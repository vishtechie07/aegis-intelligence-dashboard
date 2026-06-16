package com.aegis.dto;

/** Cited article used in an Ask Agent deep-dive response. */
public record DeepDiveSource(
        Long newsId,
        String title,
        String excerpt,
        String sourceUrl,
        boolean currentArticle) {}
