package com.aegis.controller;

import com.aegis.dto.DeepDiveHistoryEntry;
import com.aegis.dto.DeepDiveResponse;
import com.aegis.dto.DeepDiveSource;
import com.aegis.dto.InsightEvent;
import com.aegis.dto.InsightFeedPage;
import com.aegis.service.DeepDiveService;
import com.aegis.service.InsightService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@WebFluxTest(InsightController.class)
@TestPropertySource(properties = "spring.ai.openai.api-key=sk-test-placeholder")
@SuppressWarnings({"null", "DataFlowIssue"})
class InsightControllerTest {

    @Autowired WebTestClient client;
    @Autowired InsightController controller;
    @MockitoBean InsightService insightService;
    @MockitoBean DeepDiveService deepDiveService;

    @Test
    void getLatest_returnsInsightList() {
        InsightEvent event = sampleEvent(1L, 7);
        when(insightService.getLatestPerCompetitor(anyInt())).thenReturn(List.of(event));

        client.get().uri("/api/insights/latest")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].id").isEqualTo(1)
                .jsonPath("$[0].threatLevel").isEqualTo(7)
                .jsonPath("$[0].competitorName").isEqualTo("OpenAI");
    }

    @Test
    void getLatest_capsLimitAt100() {
        when(insightService.getLatestPerCompetitor(100)).thenReturn(List.of());

        client.get().uri("/api/insights/latest?limitPerCompetitor=999")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void getThreats_returnsHighThreatInsights() {
        InsightEvent event = sampleEvent(2L, 9);
        when(insightService.getFeed(isNull(), isNull(), eq(7), isNull(), isNull(), isNull(), eq("threat"), eq(0), eq(50), isNull()))
                .thenReturn(new InsightFeedPage(List.of(event), 1, false));

        client.get().uri("/api/insights/threats?minLevel=7")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.items[0].threatLevel").isEqualTo(9);
    }

    @Test
    void getThreats_usesDefaultMinLevel() {
        when(insightService.getFeed(isNull(), isNull(), eq(7), isNull(), isNull(), isNull(), eq("threat"), eq(0), eq(50), isNull()))
                .thenReturn(new InsightFeedPage(List.of(), 0, false));

        client.get().uri("/api/insights/threats")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void streamEndpoint_wrapsInsightEventAsNamedSseEvent() {
        InsightEvent event = sampleEvent(1L, 8);
        when(insightService.stream()).thenReturn(Flux.just(event));

        // Test controller Flux directly — avoids blocking on infinite HTTP stream
        StepVerifier.create(
                        controller.stream()
                                .filter(sse -> sse.data() != null) // skip heartbeat comments
                                .take(1)
                )
                .assertNext(sse -> {
                    assertThat(sse.event()).isEqualTo("insight");
                    assertThat(sse.id()).isEqualTo("1");
                    assertThat(sse.data()).isNotNull();
                    assertThat(sse.data().threatLevel()).isEqualTo(8);
                })
                .thenCancel()
                .verify(Duration.ofSeconds(2));
    }

    @Test
    void deepDive_returnsAnalysis() {
        when(deepDiveService.deepDive(eq(42L), anyString(), any(), anyString()))
                .thenReturn(new DeepDiveResponse(
                        "• Strategic point 1\n• Strategic point 2",
                        List.of(new DeepDiveSource(42L, "Title", "Excerpt", "https://example.com", true)),
                        true));

        client.post().uri("/api/insights/deep-dive")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"newsId": 42, "question": "What does this mean for pricing?"}
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.analysis").isEqualTo("• Strategic point 1\n• Strategic point 2")
                .jsonPath("$.ragUsed").isEqualTo(true)
                .jsonPath("$.sources[0].newsId").isEqualTo(42)
                .jsonPath("$.sources[0].currentArticle").isEqualTo(true);
    }

    @Test
    void deepDiveHistory_returnsList() {
        var t = OffsetDateTime.now();
        when(deepDiveService.history(anyLong())).thenReturn(List.of(
                new DeepDiveHistoryEntry(1L, 42L, "Q?", "A", t, List.of(), false)));
        client.get().uri("/api/insights/deep-dive/history?newsId=42")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].newsId").isEqualTo(42)
                .jsonPath("$[0].question").isEqualTo("Q?");
    }

    @Test
    void getLatest_returnsEmptyListWhenNoData() {
        when(insightService.getLatestPerCompetitor(50)).thenReturn(List.of());

        client.get().uri("/api/insights/latest")
                .exchange()
                .expectStatus().isOk()
                .expectBody().json("[]");
    }

    @Test
    void getFeed_returnsPaginatedPage() {
        InsightEvent event = sampleEvent(3L, 6);
        when(insightService.getFeed(isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), eq("processed"), eq(0), eq(50), anyList()))
                .thenReturn(new InsightFeedPage(List.of(event), 100, true));

        client.get().uri("/api/insights/feed?limit=50")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.total").isEqualTo(100)
                .jsonPath("$.hasMore").isEqualTo(true)
                .jsonPath("$.items[0].id").isEqualTo(3);
    }

    @Test
    void getStats_returnsCounts() {
        when(insightService.getStats()).thenReturn(
                new com.aegis.dto.InsightStats(10, 8, 2, 1, 1, 0, 3));

        client.get().uri("/api/insights/stats")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.totalInsights").isEqualTo(8)
                .jsonPath("$.highThreatCount").isEqualTo(3);
    }

    private InsightEvent sampleEvent(Long id, int threatLevel) {
        return new InsightEvent(id, 10L, "OpenAI", "GPT-5 Released",
                "https://techcrunch.com/gpt5", "RSS", "Strategist",
                "PRODUCT_LAUNCH", threatLevel,
                "OpenAI released flagship model",
                "Accelerate your roadmap",
                OffsetDateTime.now(), OffsetDateTime.now(),
                "GPT-5 Released excerpt", false, null);
    }
}
