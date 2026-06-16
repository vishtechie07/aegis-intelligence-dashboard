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
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

@Service
@Slf4j
public class RagIndexingService {

    private static final int MAX_CONCURRENT_INDEX = 2;

    private final RagProperties ragProperties;
    private final DynamicChatClientProvider chatClientProvider;
    private final CompetitorNewsRepository newsRepository;
    private final VectorStore vectorStore;
    private final Semaphore indexSlots = new Semaphore(MAX_CONCURRENT_INDEX);

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
            if (!indexSlots.tryAcquire(60, TimeUnit.SECONDS)) {
                log.warn("[RAG] index skipped newsId={} (concurrency limit)", newsId);
                return;
            }
            try {
                indexNews(newsId);
            } finally {
                indexSlots.release();
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warn("[RAG] index interrupted newsId={}", newsId);
        } catch (Exception ex) {
            log.warn("[RAG] index failed newsId={}: {}", newsId, ex.getMessage());
        }
    }

    /** Sequential startup backfill — one article at a time to avoid exhausting the DB pool. */
    @Async
    public void runBackfill(List<Long> newsIds) {
        if (!ragProperties.enabled() || vectorStore == null) {
            return;
        }
        int total = newsIds.size();
        int done = 0;
        log.info("[RAG] backfill started for {} articles", total);
        for (Long newsId : newsIds) {
            try {
                indexNews(newsId);
                done++;
                if (done % 100 == 0 || done == total) {
                    log.info("[RAG] backfill progress {}/{}", done, total);
                }
            } catch (Exception ex) {
                log.warn("[RAG] backfill failed newsId={}: {}", newsId, ex.getMessage());
            }
        }
        log.info("[RAG] backfill finished {}/{}", done, total);
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
