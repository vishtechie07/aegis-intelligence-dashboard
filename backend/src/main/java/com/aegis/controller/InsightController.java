package com.aegis.controller;

import com.aegis.dto.DeepDiveHistoryEntry;
import com.aegis.dto.DeepDiveRequest;
import com.aegis.dto.InsightEvent;
import com.aegis.service.DeepDiveService;
import com.aegis.service.DemoQuotaService;
import com.aegis.service.InsightService;
import com.aegis.util.ClientAddressResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/insights")
@RequiredArgsConstructor
@SuppressWarnings("null")
public class InsightController {

    private final InsightService insightService;
    private final DeepDiveService deepDiveService;

    /** SSE stream. */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<InsightEvent>> stream() {
        return insightService.stream()
                .map(event -> ServerSentEvent.<InsightEvent>builder()
                        .id(String.valueOf(event.id()))
                        .event("insight")
                        .data(event)
                        .build())
                .mergeWith(heartbeat());
    }

    @GetMapping("/latest")
    public List<InsightEvent> latest(@RequestParam(defaultValue = "50") int limitPerCompetitor) {
        return insightService.getLatestPerCompetitor(Math.min(limitPerCompetitor, 100));
    }

    @GetMapping("/threats")
    public List<InsightEvent> threats(@RequestParam(defaultValue = "7") int minLevel) {
        return insightService.getHighThreat(minLevel);
    }

    /** Deep-dive by newsId + question. */
    @PostMapping("/deep-dive")
    public Map<String, String> deepDive(
            ServerHttpRequest request,
            @RequestBody DeepDiveRequest requestBody,
            @RequestHeader(value = DemoQuotaService.SESSION_HEADER, required = false) String sessionId) {
        if (requestBody == null) return Map.of("analysis", "");
        Long newsId = requestBody.newsId();
        String question = requestBody.question() != null ? requestBody.question() : "";
        String clientIp = ClientAddressResolver.resolve(request);
        String analysis = deepDiveService.deepDive(newsId, question, sessionId, clientIp);
        return Map.of("analysis", analysis != null ? analysis : "");
    }

    @GetMapping("/deep-dive/history")
    public List<DeepDiveHistoryEntry> deepDiveHistory(@RequestParam Long newsId) {
        return deepDiveService.history(newsId);
    }

    /** Keep SSE connections alive through proxies */
    private Flux<ServerSentEvent<InsightEvent>> heartbeat() {
        return Flux.interval(Duration.ofSeconds(30))
                .map(tick -> ServerSentEvent.<InsightEvent>builder()
                        .comment("heartbeat")
                        .build());
    }
}
