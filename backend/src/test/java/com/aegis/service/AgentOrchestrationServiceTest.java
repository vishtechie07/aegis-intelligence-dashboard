package com.aegis.service;

import com.aegis.agent.MarketAnalystAgent;
import com.aegis.agent.NoiseCancelerAgent;
import com.aegis.agent.StrategistAgent;
import com.aegis.dto.NewsArticle;
import com.aegis.entity.AgentInsight;
import com.aegis.entity.CompetitorNews;
import com.aegis.repository.AgentInsightRepository;
import com.aegis.repository.CompetitorNewsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings({"null", "DataFlowIssue"})
class AgentOrchestrationServiceTest {

    @Mock NoiseCancelerAgent noiseCanceler;
    @Mock MarketAnalystAgent marketAnalyst;
    @Mock StrategistAgent strategist;
    @Mock CompetitorNewsRepository newsRepository;
    @Mock AgentInsightRepository insightRepository;
    @Mock InsightService insightService;
    @Mock RagIndexingService ragIndexingService;

    AgentOrchestrationService service;

    NewsArticle article = new NewsArticle("OpenAI", "GPT-5 launched", "Full content",
            "https://example.com/gpt5", OffsetDateTime.now());

    CompetitorNews news = CompetitorNews.builder()
            .id(42L).competitorName("OpenAI").title("GPT-5 launched")
            .sourceUrl("https://example.com/gpt5").sourceType("RSS").build();

    @BeforeEach
    void setUp() {
        service = new AgentOrchestrationService(
                noiseCanceler, marketAnalyst, strategist,
                newsRepository, insightRepository, insightService, ragIndexingService);
    }

    @Test
    void processAsync_savesInsightAndPublishesEventWhenRelevant() {
        when(noiseCanceler.isRelevant(anyString(), anyString(), anyString())).thenReturn(true);
        when(marketAnalyst.categorize(anyString(), anyString(), anyString()))
                .thenReturn(MarketAnalystAgent.Category.PRODUCT_LAUNCH);
        when(strategist.analyze(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new StrategistAgent.StrategistResult(8, "Summary", "Advice"));
        when(newsRepository.getReferenceById(42L)).thenReturn(news);
        ArgumentCaptor<AgentInsight> captor = ArgumentCaptor.forClass(AgentInsight.class);
        when(insightRepository.save(captor.capture())).thenAnswer(inv -> {
            AgentInsight i = inv.getArgument(0);
            return AgentInsight.builder().id(99L).news(i.getNews())
                    .agentName(i.getAgentName()).category(i.getCategory())
                    .threatLevel(i.getThreatLevel()).summary(i.getSummary())
                    .strategicAdvice(i.getStrategicAdvice()).processedAt(OffsetDateTime.now()).build();
        });

        service.processAsync(article, 42L);

        AgentInsight saved = captor.getValue();
        assertThat(saved.getThreatLevel()).isEqualTo(8);
        assertThat(saved.getCategory()).isEqualTo("PRODUCT_LAUNCH");
        assertThat(saved.getSummary()).isEqualTo("Summary");
        verify(insightService).publish(any());
        verify(ragIndexingService).indexNewsAsync(42L);
    }

    @Test
    void processAsync_skipsWhenNoiseCancelerFilters() {
        when(noiseCanceler.isRelevant(anyString(), anyString(), anyString())).thenReturn(false);

        service.processAsync(article, 42L);

        verify(marketAnalyst, never()).categorize(anyString(), anyString(), anyString());
        verify(insightRepository, never()).save(any());
        verify(insightService, never()).publish(any());
    }

    @Test
    void processAsync_stillSavesWhenStrategistReturningFallback() {
        when(noiseCanceler.isRelevant(anyString(), anyString(), anyString())).thenReturn(true);
        when(marketAnalyst.categorize(anyString(), anyString(), anyString()))
                .thenReturn(MarketAnalystAgent.Category.OTHER);
        when(strategist.analyze(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new StrategistAgent.StrategistResult(5, "Analysis unavailable.", "Monitor closely."));
        when(newsRepository.getReferenceById(42L)).thenReturn(news);
        when(insightRepository.save(any())).thenAnswer(inv -> {
            AgentInsight i = inv.getArgument(0);
            return AgentInsight.builder().id(1L).news(i.getNews()).agentName(i.getAgentName())
                    .category(i.getCategory()).threatLevel(i.getThreatLevel()).summary(i.getSummary())
                    .strategicAdvice(i.getStrategicAdvice()).processedAt(OffsetDateTime.now()).build();
        });

        service.processAsync(article, 42L);

        verify(insightRepository).save(any());
        verify(insightService).publish(any());
    }
}
