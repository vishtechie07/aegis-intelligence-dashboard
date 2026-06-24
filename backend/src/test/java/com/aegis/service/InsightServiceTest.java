package com.aegis.service;

import com.aegis.dto.InsightEvent;
import com.aegis.entity.AgentInsight;
import com.aegis.entity.CompetitorNews;
import com.aegis.repository.AgentInsightRepository;
import com.aegis.repository.CompetitorNewsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.test.StepVerifier;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings({"DataFlowIssue", "OptionalGetWithoutIsPresent"})
class InsightServiceTest {

    @Mock AgentInsightRepository insightRepository;
    @Mock CompetitorNewsRepository newsRepository;
    @Mock CompetitorService competitorService;
    @Mock RagAvailabilityService ragAvailabilityService;
    @Mock RagRetrievalService ragRetrievalService;
    @Mock InsightStoryClusterService storyClusterService;

    InsightService service;

    @BeforeEach
    void setUp() {
        service = new InsightService(
                insightRepository,
                newsRepository,
                competitorService,
                ragAvailabilityService,
                ragRetrievalService,
                storyClusterService);
    }

    @Test
    void publish_emitsEventToStream() {
        InsightEvent event = sampleEvent(1L);

        StepVerifier.create(service.stream().take(1))
                .then(() -> service.publish(event))
                .expectNextMatches(e -> e.id().equals(1L))
                .verifyComplete();
    }

    @Test
    void publish_multipleEventsReceivedInOrder() {
        InsightEvent e1 = sampleEvent(10L);
        InsightEvent e2 = sampleEvent(11L);

        StepVerifier.create(service.stream().take(2))
                .then(() -> {
                    service.publish(e1);
                    service.publish(e2);
                })
                .expectNextMatches(e -> e.id().equals(10L))
                .expectNextMatches(e -> e.id().equals(11L))
                .verifyComplete();
    }

    @Test
    void getLatest_delegatesToRepository() {
        AgentInsight insight = buildInsight(1L, 8);
        when(insightRepository.findLatestWithNews(any())).thenReturn(List.of(insight));
        when(ragAvailabilityService.indexedNewsIds(any())).thenReturn(Set.of());

        List<InsightEvent> results = service.getLatest(5);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).threatLevel()).isEqualTo(8);
    }

    @Test
    void getLatestPerCompetitor_returnsMergedFromEachCompetitor() {
        when(competitorService.getNames()).thenReturn(List.of("Acme", "Beta"));
        AgentInsight insight1 = buildInsight(1L, 6);
        AgentInsight insight2 = buildInsight(2L, 7);
        when(ragAvailabilityService.indexedNewsIds(any())).thenReturn(Set.of());
        when(insightRepository.findLatestWithNewsByCompetitor(eq("Acme"), any())).thenReturn(List.of(insight1));
        when(insightRepository.findLatestWithNewsByCompetitor(eq("Beta"), any())).thenReturn(List.of(insight2));

        List<InsightEvent> results = service.getLatestPerCompetitor(50);

        assertThat(results).hasSize(2);
        assertThat(results).extracting(InsightEvent::competitorName).containsExactlyInAnyOrder("TestCorp", "TestCorp");
    }

    @Test
    void getHighThreat_delegatesToRepository() {
        AgentInsight insight = buildInsight(2L, 9);
        when(insightRepository.findHighThreat(7)).thenReturn(List.of(insight));
        when(ragAvailabilityService.indexedNewsIds(any())).thenReturn(Set.of());

        List<InsightEvent> results = service.getHighThreat(7);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).threatLevel()).isEqualTo(9);
    }

    @Test
    void toEvent_mapsAllFields() {
        AgentInsight insight = buildInsight(10L, 7);
        when(ragAvailabilityService.indexedNewsIds(any())).thenReturn(Set.of(100L));
        InsightEvent event = service.toEvent(insight);

        assertThat(event.id()).isEqualTo(10L);
        assertThat(event.newsId()).isEqualTo(100L);
        assertThat(event.competitorName()).isEqualTo("TestCorp");
        assertThat(event.threatLevel()).isEqualTo(7);
        assertThat(event.category()).isEqualTo("PRODUCT_LAUNCH");
        assertThat(event.sourceType()).isEqualTo("RSS");
        assertThat(event.ragAvailable()).isTrue();
        assertThat(event.contentExcerpt()).contains("Big announcement");
    }

    private InsightEvent sampleEvent(Long id) {
        return new InsightEvent(id, 1L, "Acme", "Title", "https://url.com",
                "GDELT", "Strategist", "PRODUCT_LAUNCH", 7,
                "Summary", "Advice", OffsetDateTime.now(), OffsetDateTime.now(),
                "Title excerpt", false, null);
    }

    private AgentInsight buildInsight(Long id, int threatLevel) {
        CompetitorNews news = CompetitorNews.builder()
                .id(100L)
                .competitorName("TestCorp")
                .title("Big announcement")
                .sourceUrl("https://techcrunch.com/article")
                .sourceType("RSS")
                .content("Article body for excerpt test")
                .publishedAt(OffsetDateTime.now())
                .build();

        return AgentInsight.builder()
                .id(id)
                .news(news)
                .agentName("Strategist")
                .category("PRODUCT_LAUNCH")
                .threatLevel(threatLevel)
                .summary("Summary text")
                .strategicAdvice("Strategic advice text")
                .processedAt(OffsetDateTime.now())
                .build();
    }
}
