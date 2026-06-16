package com.aegis.service;

import com.aegis.config.RagProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RagRetrievalServiceTest {

    @Mock VectorStore vectorStore;

    @Test
    void returnsEmptyWhenRagDisabled() {
        var service = new RagRetrievalService(new RagProperties(false, false, 5, 1200, 150, 20), vectorStore);
        assertThat(service.relatedContext("pricing?", "OpenAI", 1L)).isEmpty();
        verify(vectorStore, never()).similaritySearch(any(SearchRequest.class));
    }

    @Test
    void formatsRetrievedChunksAndSkipsCurrentArticle() {
        var service = new RagRetrievalService(new RagProperties(true, false, 5, 1200, 150, 20), vectorStore);
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(
                new Document("news-1-chunk-0", "Current article body", Map.of(
                        "news_id", "1", "competitor_name", "OpenAI", "title", "Current")),
                new Document("news-9-chunk-0", "Older launch context", Map.of(
                        "news_id", "9", "competitor_name", "OpenAI", "title", "Prior launch"))));

        String context = service.relatedContext("pricing impact?", "OpenAI", 1L);

        assertThat(context).contains("Prior launch").contains("Older launch");
        assertThat(context).doesNotContain("Current article body");
    }
}
