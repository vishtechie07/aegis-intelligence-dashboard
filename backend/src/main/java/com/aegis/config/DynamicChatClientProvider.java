package com.aegis.config;

import com.aegis.util.SessionIds;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@SuppressWarnings("deprecation")
public class DynamicChatClientProvider {

    private static final Logger log = LoggerFactory.getLogger(DynamicChatClientProvider.class);
    private final Map<String, ChatClient> sessionClients = new ConcurrentHashMap<>();
    private final ChatClient envClient;

    public static final String PLACEHOLDER_KEY = "sk-placeholder-no-real-calls";

    public static boolean isPlaceholderKey(String key) {
        return key != null && PLACEHOLDER_KEY.equals(key.trim());
    }

    public DynamicChatClientProvider(
            @Value("${spring.ai.openai.api-key:}") String envKey,
            @Autowired(required = false) ChatModel autoConfiguredModel) {

        String key = envKey != null ? envKey : "";
        boolean validEnvKey = !key.isBlank() && !PLACEHOLDER_KEY.equals(key.trim());
        if (validEnvKey && autoConfiguredModel != null) {
            envClient = ChatClient.builder(autoConfiguredModel).build();
            log.info("AI ChatClient initialised from environment key");
        } else {
            envClient = null;
            if (key.contains("placeholder")) {
                log.info("Placeholder API key detected - AI agents inactive until key is set via Settings");
            } else {
                log.warn("No OpenAI API key - AI features disabled until key is set via Settings or OPENAI_API_KEY");
            }
        }
    }

    public synchronized void updateKey(String sessionId, String apiKey) {
        if (apiKey == null || apiKey.isBlank()) return;
        String sid = requireSessionId(sessionId);
        sessionClients.put(sid, buildClient(apiKey));
        log.debug("ChatClient updated for session {}", sid);
    }

    public synchronized void clearRuntimeKey(String sessionId) {
        String sid = requireSessionId(sessionId);
        sessionClients.remove(sid);
        log.info("Runtime API key cleared for session {}", sid);
    }

    /** Harvest pipeline and background agents — environment key only. */
    public ChatClient get() {
        if (envClient == null) {
            throw new ApiKeyNotConfiguredException(
                    "OpenAI API key not configured. Please add it via the Settings panel.");
        }
        return envClient;
    }

    /** Interactive AI (deep-dive, lookup) — per-session key, else environment key. */
    public ChatClient getForSession(String sessionId) {
        String sid = SessionIds.normalize(sessionId);
        if (sid != null) {
            ChatClient sessionClient = sessionClients.get(sid);
            if (sessionClient != null) {
                return sessionClient;
            }
        }
        return get();
    }

    public boolean isConfiguredForSession(String sessionId) {
        return isRuntimeKeySet(sessionId) || isServerKeyAvailable();
    }

    public boolean isRuntimeKeySet(String sessionId) {
        String sid = SessionIds.normalize(sessionId);
        return sid != null && sessionClients.containsKey(sid);
    }

    public boolean isServerKeyAvailable() {
        return envClient != null;
    }

    private static String requireSessionId(String sessionId) {
        String sid = SessionIds.normalize(sessionId);
        if (sid == null) {
            throw new IllegalArgumentException("Valid X-Aegis-Session header is required");
        }
        return sid;
    }

    private static ChatClient buildClient(String apiKey) {
        OpenAiApi api = new OpenAiApi(apiKey);
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model("gpt-4o-mini")
                .temperature(0.3)
                .build();
        OpenAiChatModel model = new OpenAiChatModel(api, options);
        return ChatClient.builder(model).build();
    }
}
