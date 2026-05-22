package com.aegis.util;

public final class SessionIds {

    public static final int MAX_SESSION_ID_LENGTH = 64;
    public static final int MAX_API_KEY_LENGTH = 256;
    public static final int MAX_DEEP_DIVE_QUESTION_LENGTH = 4000;

    private SessionIds() {}

    public static String normalize(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return null;
        }
        String trimmed = sessionId.trim();
        if (trimmed.length() > MAX_SESSION_ID_LENGTH) {
            return trimmed.substring(0, MAX_SESSION_ID_LENGTH);
        }
        return trimmed;
    }

    public static boolean isValid(String sessionId) {
        return normalize(sessionId) != null;
    }
}
