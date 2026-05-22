package com.aegis.service;

import com.aegis.exception.RateLimitExceededException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class InteractiveAiRateLimiter {

    private final int maxPerMinute;
    private final Map<String, Deque<Instant>> windows = new ConcurrentHashMap<>();

    public InteractiveAiRateLimiter(
            @Value("${aegis.security.interactive-max-per-minute:30}") int maxPerMinute) {
        this.maxPerMinute = Math.max(1, maxPerMinute);
    }

    public void assertAllowed(String quotaKey) {
        if (quotaKey == null || quotaKey.isBlank()) {
            return;
        }
        Instant cutoff = Instant.now().minusSeconds(60);
        Deque<Instant> window = windows.computeIfAbsent(quotaKey, k -> new ArrayDeque<>());
        synchronized (window) {
            while (!window.isEmpty() && window.peekFirst().isBefore(cutoff)) {
                window.removeFirst();
            }
            if (window.size() >= maxPerMinute) {
                throw new RateLimitExceededException(
                        "Too many AI requests. Please wait a moment and try again.");
            }
            window.addLast(Instant.now());
        }
    }
}
