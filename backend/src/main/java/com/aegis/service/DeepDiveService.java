package com.aegis.service;

import com.aegis.config.DynamicChatClientProvider;
import com.aegis.dto.DeepDiveHistoryEntry;
import com.aegis.dto.DeepDiveResponse;
import com.aegis.dto.DeepDiveSource;
import com.aegis.dto.RagRetrievalResult;
import com.aegis.entity.CompetitorNews;
import com.aegis.entity.DeepDiveLog;
import com.aegis.repository.CompetitorNewsRepository;
import com.aegis.repository.DeepDiveLogRepository;
import com.aegis.util.DeepDiveRelevanceGuard;
import com.aegis.util.NewsTextSanitizer;
import com.aegis.util.SessionIds;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeepDiveService {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<List<DeepDiveSource>> SOURCE_LIST = new TypeReference<>() {};

    private final DynamicChatClientProvider provider;
    private final DemoQuotaService demoQuotaService;
    private final InteractiveAiRateLimiter rateLimiter;
    private final CompetitorNewsRepository newsRepository;
    private final DeepDiveLogRepository deepDiveLogRepository;
    private final RagRetrievalService ragRetrievalService;

    private static final String PROMPT = """
            You are a senior competitive intelligence analyst.
            A user asked: "{question}"

            Use the following news article about {competitor} to provide a deep-dive analysis:
            Title: {title}
            Content: {content}

            Related competitor history (retrieved from prior news; use only if relevant):
            {relatedContext}

            Rules:
            - Only answer questions that relate to this article, the competitor, market impact, or strategic response.
            - If the question is unrelated (math, trivia, jokes, general chat), do NOT analyse the article. Instead reply using the off-topic format below.

            Return PLAIN TEXT ONLY. Do not use Markdown and do not use '*', '-', or '**'.
            Use '• ' (bullet character) for bullets.

            On-topic output format (exact section headers; keep ordering):

            Answer:
            <one concise paragraph>

            Strategic implications:
            • <3-5 bullets>

            Recommended actions:
            • <3-5 bullets>

            Off-topic output format (use when question is unrelated):

            Answer:
            <one sentence explaining you only answer strategic questions about this news item>

            Strategic implications:
            • N/A — question is outside competitive intelligence scope.

            Recommended actions:
            • Rephrase to reference the headline, competitor move, or market impact.""";

    @Transactional
    @SuppressWarnings("null")
    public DeepDiveResponse deepDive(Long newsId, String question, String sessionId, String clientIp) {
        demoQuotaService.assertAskAgentAllowed(sessionId, clientIp);
        rateLimiter.assertAllowed(demoQuotaService.resolveQuotaKey(sessionId, clientIp));
        String rawQ = question != null ? question.trim() : "";
        final String q = rawQ.length() > SessionIds.MAX_DEEP_DIVE_QUESTION_LENGTH
                ? rawQ.substring(0, SessionIds.MAX_DEEP_DIVE_QUESTION_LENGTH)
                : rawQ;

        CompetitorNews news = newsRepository.findById(newsId)
                .orElseThrow(() -> new IllegalArgumentException("News not found: " + newsId));

        if (DeepDiveRelevanceGuard.isObviouslyOffTopic(q)) {
            return persistAndReturn(newsId, q, DeepDiveRelevanceGuard.offTopicResponse(),
                    List.of(currentSource(news)), false);
        }

        RagRetrievalResult retrieval = ragRetrievalService.retrieve(q, news);
        String stripped = news.getContent() != null ? NewsTextSanitizer.stripHtml(news.getContent()) : "";
        String safeContent = stripped.isBlank()
                ? "No content available"
                : stripped.substring(0, Math.min(stripped.length(), 1000));
        String relatedContext = retrieval.relatedContext().isBlank()
                ? "None available."
                : retrieval.relatedContext();

        Object raw = provider.getForSession(sessionId).prompt()
                .user(u -> u.text(PROMPT)
                        .param("question", q)
                        .param("competitor", news.getCompetitorName() != null ? news.getCompetitorName() : "")
                        .param("title", news.getTitle() != null ? news.getTitle() : "")
                        .param("content", safeContent)
                        .param("relatedContext", relatedContext))
                .call()
                .content();
        String analysis = normalizePlainText(raw != null ? raw.toString() : "");
        return persistAndReturn(newsId, q, analysis, retrieval.sources(), retrieval.ragUsed());
    }

    private DeepDiveSource currentSource(CompetitorNews news) {
        String title = news.getTitle() != null ? news.getTitle() : "";
        String content = news.getContent() != null ? news.getContent() : "";
        String excerpt = content.length() > 400 ? content.substring(0, 400) + "…" : content;
        if (excerpt.isBlank()) {
            excerpt = title;
        }
        return new DeepDiveSource(news.getId(), title, excerpt, news.getSourceUrl(), true);
    }

    private DeepDiveResponse persistAndReturn(
            Long newsId,
            String question,
            String analysis,
            List<DeepDiveSource> sources,
            boolean ragUsed) {
        if (!question.isBlank()) {
            deepDiveLogRepository.save(DeepDiveLog.builder()
                    .newsId(newsId)
                    .question(question)
                    .analysis(analysis)
                    .sourcesJson(serializeSources(sources))
                    .ragUsed(ragUsed)
                    .build());
        }
        return new DeepDiveResponse(analysis, sources != null ? sources : List.of(), ragUsed);
    }

    public List<DeepDiveHistoryEntry> history(Long newsId) {
        return deepDiveLogRepository.findTop20ByNewsIdOrderByCreatedAtDesc(newsId).stream()
                .map(this::toHistoryEntry)
                .toList();
    }

    public List<DeepDiveHistoryEntry> recentHistory() {
        return deepDiveLogRepository.findTop30ByOrderByCreatedAtDesc().stream()
                .map(this::toHistoryEntry)
                .toList();
    }

    private DeepDiveHistoryEntry toHistoryEntry(DeepDiveLog log) {
        return new DeepDiveHistoryEntry(
                log.getId(),
                log.getNewsId(),
                log.getQuestion() != null ? log.getQuestion() : "",
                log.getAnalysis() != null ? log.getAnalysis() : "",
                log.getCreatedAt(),
                deserializeSources(log.getSourcesJson()),
                log.isRagUsed());
    }

    private static String serializeSources(List<DeepDiveSource> sources) {
        if (sources == null || sources.isEmpty()) {
            return null;
        }
        try {
            return JSON.writeValueAsString(sources);
        } catch (Exception ex) {
            log.warn("[RAG] failed to serialize sources: {}", ex.getMessage());
            return null;
        }
    }

    private static List<DeepDiveSource> deserializeSources(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return JSON.readValue(json, SOURCE_LIST);
        } catch (Exception ex) {
            return List.of();
        }
    }

    private String normalizePlainText(String text) {
        if (text == null || text.isBlank()) return "";
        String t = text.replace("\r\n", "\n");
        t = t.replaceAll("(?m)^\\s*#{1,6}\\s*", "");
        t = t.replace("**", "");
        t = t.replaceAll("`{1,3}", "");
        t = t.replaceAll("(?m)^\\s*[-*]\\s+", "• ");
        return t.trim();
    }
}
