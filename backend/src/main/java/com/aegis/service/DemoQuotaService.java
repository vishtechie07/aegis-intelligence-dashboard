package com.aegis.service;

import com.aegis.config.DynamicChatClientProvider;
import com.aegis.dto.DemoQuotaStatus;
import com.aegis.exception.DemoQuotaExceededException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class DemoQuotaService {

    public static final String SESSION_HEADER = "X-Aegis-Session";

    private final DynamicChatClientProvider chatClientProvider;
    private final int trialMinutes;
    private final boolean trialEnabled;
    private final Map<String, Instant> quotaStarts = new ConcurrentHashMap<>();

    public DemoQuotaService(
            DynamicChatClientProvider chatClientProvider,
            @Value("${aegis.demo.trial-minutes:5}") int trialMinutes,
            @Value("${aegis.demo.trial-enabled:true}") boolean trialEnabled) {
        this.chatClientProvider = chatClientProvider;
        this.trialMinutes = Math.max(1, trialMinutes);
        this.trialEnabled = trialEnabled;
    }

    public void assertInteractiveAiAllowed(String sessionId, String clientIp) {
        if (!trialEnabled || !chatClientProvider.isServerKeyAvailable()) {
            return;
        }
        if (chatClientProvider.isRuntimeKeySet(sessionId)) {
            return;
        }
        String quotaKey = resolveQuotaKey(sessionId, clientIp);
        Instant start = quotaStarts.computeIfAbsent(quotaKey, k -> Instant.now());
        Duration elapsed = Duration.between(start, Instant.now());
        if (elapsed.toMinutes() >= trialMinutes) {
            throw new DemoQuotaExceededException(
                    "Demo period ended (%d minutes). Add your OpenAI API key in Settings to continue using Ask Agent and AI Lookup."
                            .formatted(trialMinutes));
        }
    }

    /** Hosted demo trial is keyed by client IP so rotating X-Aegis-Session cannot reset the clock. */
    public String resolveQuotaKey(String sessionId, String clientIp) {
        String ip = clientIp != null && !clientIp.isBlank() ? clientIp.trim() : "unknown";
        return "ip:" + ip;
    }

    public DemoQuotaStatus status(String sessionId, String clientIp) {
        boolean hosted = chatClientProvider.isServerKeyAvailable()
                && !chatClientProvider.isRuntimeKeySet(sessionId);
        if (!trialEnabled || !chatClientProvider.isServerKeyAvailable()) {
            return new DemoQuotaStatus(false, hosted, false, trialMinutes, 0);
        }
        if (chatClientProvider.isRuntimeKeySet(sessionId)) {
            return new DemoQuotaStatus(trialEnabled, false, false, trialMinutes, 0);
        }
        String quotaKey = resolveQuotaKey(sessionId, clientIp);
        Instant start = quotaStarts.computeIfAbsent(quotaKey, k -> Instant.now());
        long totalSec = trialMinutes * 60L;
        long elapsedSec = Duration.between(start, Instant.now()).getSeconds();
        long remaining = Math.max(0, totalSec - elapsedSec);
        return new DemoQuotaStatus(true, true, remaining <= 0, trialMinutes, remaining);
    }

    void resetQuota(String sessionId, String clientIp) {
        quotaStarts.remove(resolveQuotaKey(sessionId, clientIp));
    }

    /** Package-visible for unit tests. */
    void seedQuotaStart(String sessionId, String clientIp, Instant start) {
        quotaStarts.put(resolveQuotaKey(sessionId, clientIp), start);
    }
}
