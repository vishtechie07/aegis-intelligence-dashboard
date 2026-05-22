package com.aegis.dto;

/** Interactive AI trial state for browser sessions using the hosted OpenAI key. */
public record DemoQuotaStatus(
        boolean trialEnabled,
        boolean usingHostedKey,
        boolean requiresUserKey,
        int trialMinutes,
        long secondsRemaining) {}
