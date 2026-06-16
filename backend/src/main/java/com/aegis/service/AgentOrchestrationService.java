package com.aegis.service;

import com.aegis.agent.MarketAnalystAgent;
import com.aegis.agent.NoiseCancelerAgent;
import com.aegis.agent.StrategistAgent;
import com.aegis.dto.NewsArticle;
import com.aegis.entity.AgentInsight;
import com.aegis.entity.CompetitorNews;
import com.aegis.repository.AgentInsightRepository;
import com.aegis.repository.CompetitorNewsRepository;
import com.aegis.util.ThreatLevelAdjuster;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
@SuppressWarnings("null")
public class AgentOrchestrationService {

    private final NoiseCancelerAgent noiseCanceler;
    private final MarketAnalystAgent marketAnalyst;
    private final StrategistAgent strategist;
    private final CompetitorNewsRepository newsRepository;
    private final AgentInsightRepository insightRepository;
    private final InsightService insightService;
    private final RagIndexingService ragIndexingService;

    @Async
    @Transactional
    public void processAsync(NewsArticle article, Long newsId) {
        log.info("[Agent] pipeline start newsId={} competitor={}", newsId, article.competitor());

        if (!noiseCanceler.isRelevant(article.competitor(), article.title(), article.description())) {
            log.info("[Agent] noise filtered newsId={} competitor={}", newsId, article.competitor());
            return;
        }

        CompetitorNews news = newsRepository.getReferenceById(newsId);
        String sourceType = news.getSourceType() != null ? news.getSourceType() : "";

        MarketAnalystAgent.Category category = marketAnalyst.categorize(
                article.competitor(), article.title(), article.description());

        StrategistAgent.StrategistResult result = strategist.analyze(
                article.competitor(), category.name(), article.title(), article.description());

        int threat = ThreatLevelAdjuster.adjust(category.name(), sourceType, result.threatLevel());
        AgentInsight insight = AgentInsight.builder()
                .news(news)
                .agentName("Strategist")
                .category(category.name())
                .threatLevel(threat)
                .summary(result.summary() != null ? result.summary() : "")
                .strategicAdvice(result.strategicAdvice() != null ? result.strategicAdvice() : "")
                .build();
        AgentInsight saved = insightRepository.save(insight);
        log.info("[Agent] insight saved id={} threat={}/10 competitor={} category={}",
                saved.getId(), threat, article.competitor(), category);

        insightService.publish(insightService.toEvent(saved));
        ragIndexingService.indexNewsAsync(newsId);
    }
}
