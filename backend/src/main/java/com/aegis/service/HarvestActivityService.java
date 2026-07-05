package com.aegis.service;

import com.aegis.repository.CompetitorNewsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class HarvestActivityService {

    private final CompetitorNewsRepository newsRepository;
    private final ConcurrentHashMap<String, Instant> lastRunUtc = new ConcurrentHashMap<>();

    public void record(String sourceKey) {
        if (sourceKey != null && !sourceKey.isBlank()) {
            lastRunUtc.put(sourceKey.trim(), Instant.now());
        }
    }

    public Map<String, String> snapshotIso() {
        Map<String, Instant> merged = new HashMap<>();
        for (var row : newsRepository.findLatestHarvestBySource()) {
            if (row.sourceType() != null && row.lastAt() != null) {
                merged.put(row.sourceType(), row.lastAt().toInstant());
            }
        }
        lastRunUtc.forEach((key, instant) ->
                merged.merge(key, instant, (a, b) -> a.isAfter(b) ? a : b));
        return merged.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().toString()));
    }
}
