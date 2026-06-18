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
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class DemoQuotaService {

    public static final String SESSION_HEADER = "X-Aegis-Session";

    private final DynamicChatClientProvider chatClientProvider;
    private final int trialMinutes;
    private final boolean trialEnabled;
    private final int askAgentGraceUses;
    private final Map<String, Instant> quotaStarts = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> askAgentGraceUsed = new ConcurrentHashMap<>();

    public DemoQuotaService(
            DynamicChatClientProvider chatClientProvider,
            @Value("${aegis.demo.trial-minutes:5}") int trialMinutes,
            @Value("${aegis.demo.trial-enabled:true}") boolean trialEnabled,
            @Value("${aegis.demo.ask-agent-grace-uses:5}") int askAgentGraceUses) {
        this.chatClientProvider = chatClientProvider;
        this.trialMinutes = Math.max(1, trialMinutes);
        this.trialEnabled = trialEnabled;
        this.askAgentGraceUses = Math.max(0, askAgentGraceUses);
    }

    public void assertInteractiveAiAllowed(String sessionId, String clientIp) {
        if (!trialEnabled || !chatClientProvider.isServerKeyAvailable()) {
            return;
        }
        if (chatClientProvider.isRuntimeKeySet(sessionId)) {
            return;
        }
        if (!isTrialWindowOpen(sessionId, clientIp)) {
            throw new DemoQuotaExceededException(
                    "Demo period ended (%d minutes). Add your OpenAI API key in Settings to continue using AI Lookup."
                            .formatted(trialMinutes));
        }
    }

    public void assertAskAgentAllowed(String sessionId, String clientIp) {
        if (!trialEnabled || !chatClientProvider.isServerKeyAvailable()) {
            return;
        }
        if (chatClientProvider.isRuntimeKeySet(sessionId)) {
            return;
        }
        if (isTrialWindowOpen(sessionId, clientIp)) {
            return;
        }
        if (askAgentGraceUses <= 0) {
            throw askAgentGraceExhaustedException();
        }
        String quotaKey = resolveQuotaKey(sessionId, clientIp);
        int used = askAgentGraceUsed
                .computeIfAbsent(quotaKey, k -> new AtomicInteger(0))
                .incrementAndGet();
        if (used > askAgentGraceUses) {
            askAgentGraceUsed.get(quotaKey).decrementAndGet();
            throw askAgentGraceExhaustedException();
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
            return emptyStatus(hosted);
        }
        if (chatClientProvider.isRuntimeKeySet(sessionId)) {
            return new DemoQuotaStatus(trialEnabled, false, false, trialMinutes, 0, askAgentGraceUses, askAgentGraceUses);
        }
        String quotaKey = resolveQuotaKey(sessionId, clientIp);
        Instant start = quotaStarts.computeIfAbsent(quotaKey, k -> Instant.now());
        long totalSec = trialMinutes * 60L;
        long elapsedSec = Duration.between(start, Instant.now()).getSeconds();
        long remaining = Math.max(0, totalSec - elapsedSec);
        int graceRemaining = graceRemaining(quotaKey);
        boolean timeExpired = remaining <= 0;
        boolean requiresUserKey = timeExpired && graceRemaining <= 0;
        return new DemoQuotaStatus(true, true, requiresUserKey, trialMinutes, remaining,
                askAgentGraceUses, graceRemaining);
    }

    private DemoQuotaStatus emptyStatus(boolean hosted) {
        return new DemoQuotaStatus(false, hosted, false, trialMinutes, 0, askAgentGraceUses, askAgentGraceUses);
    }

    private boolean isTrialWindowOpen(String sessionId, String clientIp) {
        String quotaKey = resolveQuotaKey(sessionId, clientIp);
        Instant start = quotaStarts.computeIfAbsent(quotaKey, k -> Instant.now());
        return Duration.between(start, Instant.now()).toMinutes() < trialMinutes;
    }

    private int graceRemaining(String quotaKey) {
        if (askAgentGraceUses <= 0) {
            return 0;
        }
        int used = askAgentGraceUsed.containsKey(quotaKey)
                ? askAgentGraceUsed.get(quotaKey).get()
                : 0;
        return Math.max(0, askAgentGraceUses - used);
    }

    private DemoQuotaExceededException askAgentGraceExhaustedException() {
        return new DemoQuotaExceededException(
                "Demo ended (%d-minute trial + %d bonus Ask Agent asks used). Add your OpenAI API key in Settings to continue."
                        .formatted(trialMinutes, askAgentGraceUses));
    }

    void resetQuota(String sessionId, String clientIp) {
        String quotaKey = resolveQuotaKey(sessionId, clientIp);
        quotaStarts.remove(quotaKey);
        askAgentGraceUsed.remove(quotaKey);
    }

    /** Package-visible for unit tests. */
    void seedQuotaStart(String sessionId, String clientIp, Instant start) {
        quotaStarts.put(resolveQuotaKey(sessionId, clientIp), start);
    }

    /** Package-visible for unit tests. */
    void seedAskAgentGraceUsed(String sessionId, String clientIp, int used) {
        askAgentGraceUsed.put(resolveQuotaKey(sessionId, clientIp), new AtomicInteger(used));
    }
}
