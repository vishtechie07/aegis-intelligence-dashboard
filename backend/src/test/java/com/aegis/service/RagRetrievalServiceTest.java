package com.aegis.service;

import com.aegis.config.RagProperties;
import com.aegis.dto.RagRetrievalResult;
import com.aegis.entity.CompetitorNews;
import com.aegis.repository.CompetitorNewsRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RagRetrievalServiceTest {

    @Mock VectorStore vectorStore;
    @Mock CompetitorNewsRepository newsRepository;

    private CompetitorNews currentArticle() {
        return CompetitorNews.builder()
                .id(1L)
                .competitorName("OpenAI")
                .title("Current")
                .content("Current article body")
                .sourceUrl("https://example.com/current")
                .build();
    }

    @Test
    void returnsCurrentArticleOnlyWhenRagDisabled() {
        var service = new RagRetrievalService(
                new RagProperties(false, false, 5, 1200, 150, 20), newsRepository, vectorStore);
        RagRetrievalResult result = service.retrieve("pricing?", currentArticle());

        assertThat(result.ragUsed()).isFalse();
        assertThat(result.relatedContext()).isEmpty();
        assertThat(result.sources()).hasSize(1);
        assertThat(result.sources().getFirst().currentArticle()).isTrue();
        verify(vectorStore, never()).similaritySearch(any(SearchRequest.class));
    }

    @Test
    void formatsRetrievedChunksAndSkipsCurrentArticle() {
        var service = new RagRetrievalService(
                new RagProperties(true, false, 5, 1200, 150, 20), newsRepository, vectorStore);
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(
                new Document("news-1-chunk-0", "Current article body", Map.of(
                        "news_id", "1", "competitor_name", "OpenAI", "title", "Current")),
                new Document("news-9-chunk-0", "Older launch context", Map.of(
                        "news_id", "9", "competitor_name", "OpenAI", "title", "Prior launch"))));
        when(newsRepository.findById(9L)).thenReturn(Optional.of(CompetitorNews.builder()
                .id(9L)
                .title("Prior launch")
                .content("Older launch context")
                .sourceUrl("https://example.com/prior")
                .build()));

        RagRetrievalResult result = service.retrieve("pricing impact?", currentArticle());

        assertThat(result.ragUsed()).isTrue();
        assertThat(result.relatedContext()).contains("Prior launch").contains("Older launch");
        assertThat(result.relatedContext()).doesNotContain("Current article body");
        assertThat(result.sources()).hasSize(2);
        assertThat(result.sources().getFirst().currentArticle()).isTrue();
        assertThat(result.sources().get(1).currentArticle()).isFalse();
        assertThat(result.sources().get(1).sourceUrl()).isEqualTo("https://example.com/prior");
    }
}
