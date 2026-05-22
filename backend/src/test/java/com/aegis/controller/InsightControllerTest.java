package com.aegis.controller;

import com.aegis.dto.DeepDiveHistoryEntry;
import com.aegis.dto.InsightEvent;
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
        when(insightService.getHighThreat(7)).thenReturn(List.of(event));

        client.get().uri("/api/insights/threats?minLevel=7")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].threatLevel").isEqualTo(9);
    }

    @Test
    void getThreats_usesDefaultMinLevel() {
        when(insightService.getHighThreat(7)).thenReturn(List.of());

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
                .thenReturn("• Strategic point 1\n• Strategic point 2");

        client.post().uri("/api/insights/deep-dive")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"newsId": 42, "question": "What does this mean for pricing?"}
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.analysis").isEqualTo("• Strategic point 1\n• Strategic point 2");
    }

    @Test
    void deepDiveHistory_returnsList() {
        var t = OffsetDateTime.now();
        when(deepDiveService.history(anyLong())).thenReturn(List.of(
                new DeepDiveHistoryEntry(1L, 42L, "Q?", "A", t)));
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

    private InsightEvent sampleEvent(Long id, int threatLevel) {
        return new InsightEvent(id, 10L, "OpenAI", "GPT-5 Released",
                "https://techcrunch.com/gpt5", "RSS", "Strategist",
                "PRODUCT_LAUNCH", threatLevel,
                "OpenAI released flagship model",
                "Accelerate your roadmap",
                OffsetDateTime.now(), OffsetDateTime.now());
    }
}
