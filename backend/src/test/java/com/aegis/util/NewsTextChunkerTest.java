package com.aegis.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NewsTextChunkerTest {

    @Test
    void returnsSingleChunkForShortText() {
        List<String> chunks = NewsTextChunker.chunk("Title", "Body", 500, 50, 10);
        assertThat(chunks).hasSize(1);
        assertThat(chunks.getFirst()).contains("Title").contains("Body");
    }

    @Test
    void splitsLongTextWithOverlap() {
        String content = "x".repeat(2500);
        List<String> chunks = NewsTextChunker.chunk("Headline", content, 1000, 100, 10);
        assertThat(chunks.size()).isGreaterThan(1);
        assertThat(chunks.size()).isLessThanOrEqualTo(10);
    }

    @Test
    void returnsEmptyForBlankInput() {
        assertThat(NewsTextChunker.chunk("  ", null, 500, 50, 5)).isEmpty();
    }
}
