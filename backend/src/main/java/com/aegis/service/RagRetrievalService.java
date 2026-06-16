package com.aegis.service;

import com.aegis.config.RagConfig;
import com.aegis.config.RagProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
@Slf4j
public class RagRetrievalService {

    private final RagProperties ragProperties;
    private final VectorStore vectorStore;

    public RagRetrievalService(
            RagProperties ragProperties,
            @Autowired(required = false) @Qualifier(RagConfig.VECTOR_STORE_BEAN) VectorStore vectorStore) {
        this.ragProperties = ragProperties;
        this.vectorStore = vectorStore;
    }

    /** Related competitor news excerpts for deep-dive context; empty when RAG is off or retrieval fails. */
    public String relatedContext(String question, String competitorName, Long currentNewsId) {
        if (!ragProperties.enabled() || vectorStore == null) {
            return "";
        }
        if (question == null || question.isBlank()) {
            return "";
        }
        try {
            var builder = new FilterExpressionBuilder();
            var filter = builder.eq("competitor_name", competitorName != null ? competitorName : "").build();
            List<Document> hits = vectorStore.similaritySearch(SearchRequest.builder()
                    .query(question)
                    .topK(ragProperties.topK())
                    .filterExpression(filter)
                    .build());
            if (hits == null || hits.isEmpty()) {
                return "";
            }
            String currentId = currentNewsId != null ? currentNewsId.toString() : "";
            StringBuilder sb = new StringBuilder();
            int n = 0;
            for (Document doc : hits) {
                String hitNewsId = metadata(doc, "news_id");
                if (!currentId.isBlank() && currentId.equals(hitNewsId)) {
                    continue;
                }
                n++;
                String title = metadata(doc, "title");
                sb.append("• [").append(title.isBlank() ? "Related article" : title).append("] ");
                String text = doc.getText();
                if (text != null && !text.isBlank()) {
                    String excerpt = text.length() > 400 ? text.substring(0, 400) + "…" : text;
                    sb.append(excerpt);
                }
                sb.append('\n');
            }
            return n > 0 ? sb.toString().trim() : "";
        } catch (Exception ex) {
            log.warn("[RAG] retrieval failed: {}", ex.getMessage());
            return "";
        }
    }

    private static String metadata(Document doc, String key) {
        Object value = doc.getMetadata().get(key);
        return value != null ? Objects.toString(value) : "";
    }
}
