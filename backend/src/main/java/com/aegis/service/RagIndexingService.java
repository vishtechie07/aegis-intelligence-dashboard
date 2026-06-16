package com.aegis.service;

import com.aegis.config.DynamicChatClientProvider;
import com.aegis.config.RagConfig;
import com.aegis.config.RagProperties;
import com.aegis.entity.CompetitorNews;
import com.aegis.repository.CompetitorNewsRepository;
import com.aegis.util.NewsTextChunker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import java.util.stream.IntStream;

@Service
@Slf4j
public class RagIndexingService {

    private final RagProperties ragProperties;
    private final DynamicChatClientProvider chatClientProvider;
    private final CompetitorNewsRepository newsRepository;
    private final VectorStore vectorStore;

    public RagIndexingService(
            RagProperties ragProperties,
            DynamicChatClientProvider chatClientProvider,
            CompetitorNewsRepository newsRepository,
            @Autowired(required = false) @Qualifier(RagConfig.VECTOR_STORE_BEAN) VectorStore vectorStore) {
        this.ragProperties = ragProperties;
        this.chatClientProvider = chatClientProvider;
        this.newsRepository = newsRepository;
        this.vectorStore = vectorStore;
    }

    @Async
    public void indexNewsAsync(Long newsId) {
        try {
            indexNews(newsId);
        } catch (Exception ex) {
            log.warn("[RAG] index failed newsId={}: {}", newsId, ex.getMessage());
        }
    }

    public void indexNews(Long newsId) {
        if (!ragProperties.enabled() || vectorStore == null) {
            return;
        }
        if (!chatClientProvider.isServerKeyAvailable()) {
            return;
        }
        CompetitorNews news = newsRepository.findById(newsId).orElse(null);
        if (news == null) {
            return;
        }
        List<String> chunks = NewsTextChunker.chunk(
                news.getTitle(),
                news.getContent(),
                ragProperties.chunkSize(),
                ragProperties.chunkOverlap(),
                ragProperties.maxChunksPerArticle());
        if (chunks.isEmpty()) {
            return;
        }

        List<String> staleIds = IntStream.range(0, ragProperties.maxChunksPerArticle())
                .mapToObj(i -> documentId(newsId, i))
                .toList();
        vectorStore.delete(staleIds);

        String competitor = news.getCompetitorName() != null ? news.getCompetitorName() : "";
        List<Document> documents = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            documents.add(new Document(
                    documentId(newsId, i),
                    chunks.get(i),
                    Map.of(
                            "news_id", newsId.toString(),
                            "competitor_name", competitor,
                            "title", news.getTitle() != null ? news.getTitle() : "",
                            "chunk_index", i)));
        }
        vectorStore.add(documents);
        log.debug("[RAG] indexed newsId={} chunks={}", newsId, chunks.size());
    }

    static String documentId(Long newsId, int chunkIndex) {
        return UUID.nameUUIDFromBytes(
                ("news-" + newsId + "-chunk-" + chunkIndex).getBytes(StandardCharsets.UTF_8))
                .toString();
    }
}
