package com.aegis.controller;

import com.aegis.dto.*;
import com.aegis.service.DeepDiveService;
import com.aegis.service.DemoQuotaService;
import com.aegis.service.InsightService;
import com.aegis.util.ClientAddressResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/insights")
@RequiredArgsConstructor
@SuppressWarnings("null")
public class InsightController {

    private final InsightService insightService;
    private final DeepDiveService deepDiveService;

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

    @GetMapping("/feed")
    public InsightFeedPage feed(
            @RequestParam(required = false) String competitor,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Integer minThreat,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime dateTo,
            @RequestParam(defaultValue = "processed") String sort,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(required = false) String ids) {
        return insightService.getFeed(competitor, category, minThreat, search, dateFrom, dateTo, sort, offset, limit, parseIds(ids));
    }

    @GetMapping("/stats")
    public InsightStats stats() {
        return insightService.getStats();
    }

    @GetMapping("/analytics")
    public InsightAnalytics analytics(@RequestParam(defaultValue = "7") int days) {
        return insightService.getAnalytics(days);
    }

    @GetMapping("/competitor/{name}/summary")
    public CompetitorInsightSummary competitorSummary(@PathVariable String name) {
        return insightService.getCompetitorSummary(name);
    }

    @GetMapping("/{newsId}/related")
    public List<RelatedInsightBrief> related(
            @PathVariable Long newsId,
            @RequestParam(defaultValue = "3") int limit) {
        return insightService.getRelated(newsId, limit);
    }

    @GetMapping("/threats")
    public InsightFeedPage threats(
            @RequestParam(defaultValue = "7") int minLevel,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "50") int limit) {
        return insightService.getFeed(null, null, minLevel, null, null, null, "threat", offset, limit, null);
    }

    @PostMapping("/deep-dive")
    public DeepDiveResponse deepDive(
            ServerHttpRequest request,
            @RequestBody DeepDiveRequest requestBody,
            @RequestHeader(value = DemoQuotaService.SESSION_HEADER, required = false) String sessionId) {
        if (requestBody == null) {
            return new DeepDiveResponse("", List.of(), false);
        }
        Long newsId = requestBody.newsId();
        String question = requestBody.question() != null ? requestBody.question() : "";
        String clientIp = ClientAddressResolver.resolve(request);
        return deepDiveService.deepDive(newsId, question, sessionId, clientIp);
    }

    @GetMapping("/deep-dive/history")
    public List<DeepDiveHistoryEntry> deepDiveHistory(@RequestParam Long newsId) {
        return deepDiveService.history(newsId);
    }

    @GetMapping("/deep-dive/history/recent")
    public List<DeepDiveHistoryEntry> recentDeepDiveHistory() {
        return deepDiveService.recentHistory();
    }

    private Flux<ServerSentEvent<InsightEvent>> heartbeat() {
        return Flux.interval(Duration.ofSeconds(30))
                .map(tick -> ServerSentEvent.<InsightEvent>builder()
                        .comment("heartbeat")
                        .build());
    }

    private static List<Long> parseIds(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Long::parseLong)
                .limit(200)
                .toList();
    }
}
