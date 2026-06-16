package com.aegis.service;

import com.aegis.config.RagConfig;
import com.aegis.config.RagProperties;
import com.aegis.dto.DeepDiveSource;
import com.aegis.dto.RagRetrievalResult;
import com.aegis.entity.CompetitorNews;
import com.aegis.repository.CompetitorNewsRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@Slf4j
public class RagRetrievalService {

    private final RagProperties ragProperties;
    private final CompetitorNewsRepository newsRepository;
    private final VectorStore vectorStore;

    public RagRetrievalService(
            RagProperties ragProperties,
            CompetitorNewsRepository newsRepository,
            @Autowired(required = false) @Qualifier(RagConfig.VECTOR_STORE_BEAN) VectorStore vectorStore) {
        this.ragProperties = ragProperties;
        this.newsRepository = newsRepository;
        this.vectorStore = vectorStore;
    }

    public RagRetrievalResult retrieve(String question, CompetitorNews currentArticle) {
        List<DeepDiveSource> sources = new ArrayList<>();
        sources.add(toSource(currentArticle, true));

        if (!ragProperties.enabled() || vectorStore == null || question == null || question.isBlank()) {
            return new RagRetrievalResult(sources, "", false);
        }

        try {
            String competitor = currentArticle.getCompetitorName() != null ? currentArticle.getCompetitorName() : "";
            var filter = new FilterExpressionBuilder().eq("competitor_name", competitor).build();
            List<Document> hits = vectorStore.similaritySearch(SearchRequest.builder()
                    .query(question)
                    .topK(ragProperties.topK())
                    .filterExpression(filter)
                    .build());

            if (hits == null || hits.isEmpty()) {
                return new RagRetrievalResult(sources, "", false);
            }

            Map<Long, DeepDiveSource> relatedByNewsId = new LinkedHashMap<>();
            String currentId = currentArticle.getId() != null ? currentArticle.getId().toString() : "";
            StringBuilder context = new StringBuilder();

            for (Document doc : hits) {
                String hitNewsId = metadata(doc, "news_id");
                if (!currentId.isBlank() && currentId.equals(hitNewsId)) {
                    continue;
                }
                Long parsedId = parseNewsId(hitNewsId);
                if (parsedId == null || relatedByNewsId.containsKey(parsedId)) {
                    continue;
                }
                CompetitorNews hitNews = newsRepository.findById(parsedId).orElse(null);
                String title = hitNews != null && hitNews.getTitle() != null
                        ? hitNews.getTitle()
                        : metadata(doc, "title");
                String excerpt = excerpt(doc.getText());
                String sourceUrl = hitNews != null ? hitNews.getSourceUrl() : null;
                DeepDiveSource related = new DeepDiveSource(parsedId, title, excerpt, sourceUrl, false);
                relatedByNewsId.put(parsedId, related);
                context.append("• [").append(title.isBlank() ? "Related article" : title).append("] ")
                        .append(excerpt)
                        .append('\n');
            }

            sources.addAll(relatedByNewsId.values());
            String relatedContext = context.isEmpty() ? "" : context.toString().trim();
            return new RagRetrievalResult(sources, relatedContext, !relatedByNewsId.isEmpty());
        } catch (Exception ex) {
            log.warn("[RAG] retrieval failed: {}", ex.getMessage());
            return new RagRetrievalResult(sources, "", false);
        }
    }

    private DeepDiveSource toSource(CompetitorNews news, boolean currentArticle) {
        String title = news.getTitle() != null ? news.getTitle() : "";
        String body = join(news.getTitle(), news.getContent());
        return new DeepDiveSource(
                news.getId(),
                title,
                excerpt(body),
                news.getSourceUrl(),
                currentArticle);
    }

    private static Long parseNewsId(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static String excerpt(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        return text.length() > 400 ? text.substring(0, 400) + "…" : text;
    }

    private static String join(String title, String content) {
        String t = title != null ? title.trim() : "";
        String c = content != null ? content.trim() : "";
        if (t.isBlank()) {
            return c;
        }
        if (c.isBlank()) {
            return t;
        }
        return t + "\n\n" + c;
    }

    private static String metadata(Document doc, String key) {
        Object value = doc.getMetadata().get(key);
        return value != null ? Objects.toString(value) : "";
    }
}
