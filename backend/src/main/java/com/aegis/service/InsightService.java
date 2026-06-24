package com.aegis.service;

import com.aegis.dto.*;
import com.aegis.entity.AgentInsight;
import com.aegis.entity.CompetitorNews;
import com.aegis.repository.AgentInsightRepository;
import com.aegis.repository.CompetitorNewsRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class InsightService {

    private static final int REPLAY_BUFFER = 250;
    private static final int EXCERPT_MAX = 400;
    private static final int HIGH_THREAT_MIN = 7;
    private static final OffsetDateTime FEED_DATE_MIN = OffsetDateTime.parse("1970-01-01T00:00:00Z");
    private static final OffsetDateTime FEED_DATE_MAX = OffsetDateTime.parse("3000-01-01T00:00:00Z");

    private final AgentInsightRepository insightRepository;
    private final CompetitorNewsRepository newsRepository;
    private final CompetitorService competitorService;
    private final RagAvailabilityService ragAvailabilityService;
    private final RagRetrievalService ragRetrievalService;
    private final InsightStoryClusterService storyClusterService;

    private final Sinks.Many<InsightEvent> sink = Sinks.many().replay().limit(REPLAY_BUFFER);

    @PostConstruct
    void init() {
        log.info("InsightService SSE sink initialized");
    }

    public void publish(InsightEvent event) {
        Sinks.EmitResult result = sink.tryEmitNext(event);
        if (result.isFailure()) {
            log.warn("Failed to emit SSE event for news {}: {}", event.newsId(), result);
        }
    }

    public Flux<InsightEvent> stream() {
        return sink.asFlux();
    }

    public List<InsightEvent> getLatest(int limit) {
        return toEvents(insightRepository.findLatestWithNews(PageRequest.of(0, Math.min(limit, 500))));
    }

    public List<InsightEvent> getLatestPerCompetitor(int limitPerCompetitor) {
        int cap = Math.min(limitPerCompetitor, 100);
        List<AgentInsight> merged = new ArrayList<>();
        for (String competitor : competitorService.getNames()) {
            merged.addAll(insightRepository.findLatestWithNewsByCompetitor(competitor, PageRequest.of(0, cap)));
        }
        merged.sort(Comparator.comparing(AgentInsight::getProcessedAt, Comparator.nullsLast(Comparator.reverseOrder())));
        return toEvents(merged);
    }

    public List<InsightEvent> getHighThreat(int minLevel) {
        return toEvents(insightRepository.findHighThreat(minLevel));
    }

    public InsightFeedPage getFeed(
            String competitor,
            String category,
            Integer minThreat,
            String search,
            OffsetDateTime dateFrom,
            OffsetDateTime dateTo,
            String sort,
            int offset,
            int limit,
            List<Long> ids) {
        if (ids != null && !ids.isEmpty()) {
            List<AgentInsight> found = insightRepository.findByIdInWithNews(ids);
            List<InsightEvent> items = storyClusterService.assignClusters(toEvents(found));
            return new InsightFeedPage(items, items.size(), false);
        }
        int safeLimit = Math.min(Math.max(limit, 1), 100);
        int safeOffset = Math.max(offset, 0);
        String comp = orEmpty(blankToNull(competitor));
        String cat = orEmpty(blankToNull(category));
        String q = orEmpty(search != null ? search.trim() : null);
        int threat = minThreat != null ? minThreat : 0;
        OffsetDateTime from = dateFrom != null ? dateFrom : FEED_DATE_MIN;
        OffsetDateTime to = dateTo != null ? dateTo : FEED_DATE_MAX;

        long total = insightRepository.countFeed(comp, cat, threat, q, from, to);
        PageRequest page = PageRequest.of(safeOffset / safeLimit, safeLimit);
        List<AgentInsight> pageItems = switch (sort != null ? sort : "processed") {
            case "published" -> insightRepository.findFeedPublishedDesc(comp, cat, threat, q, from, to, page);
            case "threat" -> insightRepository.findFeedThreatDesc(comp, cat, threat, q, from, to, page);
            default -> insightRepository.findFeedProcessedDesc(comp, cat, threat, q, from, to, page);
        };
        List<InsightEvent> items = storyClusterService.assignClusters(toEvents(pageItems));
        boolean hasMore = (long) safeOffset + items.size() < total;
        return new InsightFeedPage(items, total, hasMore);
    }

    public InsightFeedPage getFeed(
            String competitor,
            String category,
            Integer minThreat,
            String search,
            OffsetDateTime dateFrom,
            OffsetDateTime dateTo,
            String sort,
            int offset,
            int limit) {
        return getFeed(competitor, category, minThreat, search, dateFrom, dateTo, sort, offset, limit, null);
    }

    public InsightStats getStats() {
        OffsetDateTime startOfDay = OffsetDateTime.now(ZoneOffset.UTC).toLocalDate().atStartOfDay().atOffset(ZoneOffset.UTC);
        long totalArticles = newsRepository.count();
        long totalInsights = insightRepository.count();
        long filtered = newsRepository.countWithoutInsight();
        long todayHarvested = newsRepository.countByCreatedAtGreaterThanEqual(startOfDay);
        long todayAnalyzed = newsRepository.countInsightsSince(startOfDay);
        long todayFiltered = newsRepository.countWithoutInsightSince(startOfDay);
        long highThreat = insightRepository.countByThreatLevelGreaterThanEqual(HIGH_THREAT_MIN);
        return new InsightStats(totalArticles, totalInsights, filtered, todayHarvested, todayAnalyzed, todayFiltered, highThreat);
    }

    public InsightAnalytics getAnalytics(int days) {
        int safeDays = Math.min(Math.max(days, 1), 90);
        OffsetDateTime since = OffsetDateTime.now(ZoneOffset.UTC).minusDays(safeDays);
        return new InsightAnalytics(
                insightRepository.countByCategorySince(since),
                insightRepository.countBySourceSince(since),
                insightRepository.countHighThreatByCompetitorSince(since, HIGH_THREAT_MIN));
    }

    public CompetitorInsightSummary getCompetitorSummary(String competitorName) {
        String name = competitorName != null ? competitorName.trim() : "";
        long total = insightRepository.countByCompetitor(name);
        long high = insightRepository.countHighThreatByCompetitor(name, HIGH_THREAT_MIN);
        List<InsightEvent> recentHigh = toEvents(
                insightRepository.findFeedThreatDesc(name, "", HIGH_THREAT_MIN, "", FEED_DATE_MIN, FEED_DATE_MAX, PageRequest.of(0, 5)));
        return new CompetitorInsightSummary(
                name,
                total,
                high,
                insightRepository.countByCategoryForCompetitor(name),
                insightRepository.countBySourceForCompetitor(name),
                recentHigh);
    }

    public List<RelatedInsightBrief> getRelated(Long newsId, int limit) {
        return ragRetrievalService.findRelated(newsId, Math.min(Math.max(limit, 1), 10));
    }

    private List<InsightEvent> toEvents(List<AgentInsight> insights) {
        if (insights.isEmpty()) {
            return List.of();
        }
        List<Long> newsIds = insights.stream()
                .map(i -> i.getNews().getId())
                .toList();
        Set<Long> ragIndexed = ragAvailabilityService.indexedNewsIds(newsIds);
        return insights.stream()
                .map(i -> toEvent(i, ragIndexed.contains(i.getNews().getId())))
                .toList();
    }

    public InsightEvent toEvent(AgentInsight insight) {
        return toEvent(insight, ragAvailabilityService.indexedNewsIds(List.of(insight.getNews().getId()))
                .contains(insight.getNews().getId()));
    }

    private InsightEvent toEvent(AgentInsight insight, boolean ragAvailable) {
        CompetitorNews news = insight.getNews();
        return new InsightEvent(
                insight.getId(),
                news.getId(),
                news.getCompetitorName(),
                news.getTitle(),
                news.getSourceUrl(),
                news.getSourceType(),
                insight.getAgentName(),
                insight.getCategory(),
                insight.getThreatLevel(),
                insight.getSummary(),
                insight.getStrategicAdvice(),
                news.getPublishedAt(),
                insight.getProcessedAt(),
                excerpt(news.getTitle(), news.getContent()),
                ragAvailable,
                null);
    }

    private static String excerpt(String title, String content) {
        String t = title != null ? title.trim() : "";
        String c = content != null ? content.trim() : "";
        String body = c.isBlank() ? t : (t.isBlank() ? c : t + "\n\n" + c);
        if (body.isBlank()) return "";
        return body.length() > EXCERPT_MAX ? body.substring(0, EXCERPT_MAX) + "…" : body;
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank() || "all".equalsIgnoreCase(value)) {
            return null;
        }
        return value.trim();
    }

    private static String orEmpty(String value) {
        return value != null ? value : "";
    }
}
