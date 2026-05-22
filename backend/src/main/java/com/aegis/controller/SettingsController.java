package com.aegis.controller;

import com.aegis.config.DynamicChatClientProvider;
import com.aegis.dto.DemoQuotaStatus;
import com.aegis.service.DemoQuotaService;
import com.aegis.util.ClientAddressResolver;
import com.aegis.util.SessionIds;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
public class SettingsController {

    private final DynamicChatClientProvider provider;
    private final DemoQuotaService demoQuotaService;

    record KeyRequest(String apiKey) {}
    record StatusResponse(
            boolean configured,
            boolean runtimeKeySet,
            boolean serverKeyAvailable,
            DemoQuotaStatus demoQuota) {}

    @GetMapping("/status")
    public StatusResponse status(
            ServerHttpRequest request,
            @RequestHeader(value = DemoQuotaService.SESSION_HEADER, required = false) String sessionId) {
        String clientIp = ClientAddressResolver.resolve(request);
        return new StatusResponse(
                provider.isConfiguredForSession(sessionId),
                provider.isRuntimeKeySet(sessionId),
                provider.isServerKeyAvailable(),
                demoQuotaService.status(sessionId, clientIp));
    }

    @PutMapping("/openai-key")
    public ResponseEntity<Map<String, String>> updateKey(
            @RequestHeader(value = DemoQuotaService.SESSION_HEADER, required = false) String sessionId,
            @RequestBody KeyRequest req) {
        if (!SessionIds.isValid(sessionId)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Missing or invalid X-Aegis-Session header"));
        }
        if (req.apiKey() == null || req.apiKey().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "API key must not be blank"));
        }
        if (req.apiKey().length() > SessionIds.MAX_API_KEY_LENGTH) {
            return ResponseEntity.badRequest().body(Map.of("error", "API key is too long"));
        }
        if (!req.apiKey().startsWith("sk-")) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid OpenAI API key format"));
        }
        if (DynamicChatClientProvider.isPlaceholderKey(req.apiKey())) {
            return ResponseEntity.ok(Map.of("status", "Use a real API key from platform.openai.com to activate AI agents."));
        }
        provider.updateKey(sessionId, req.apiKey());
        return ResponseEntity.ok(Map.of("status", "Key accepted — AI agents are now active for this browser session"));
    }

    @DeleteMapping("/openai-key")
    public ResponseEntity<Map<String, String>> clearKey(
            @RequestHeader(value = DemoQuotaService.SESSION_HEADER, required = false) String sessionId) {
        if (!SessionIds.isValid(sessionId)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Missing or invalid X-Aegis-Session header"));
        }
        provider.clearRuntimeKey(sessionId);
        String status = provider.isConfiguredForSession(sessionId)
                ? "Reverted to server default API key"
                : "API key cleared — set OPENAI_API_KEY on the server or add a key in Settings";
        return ResponseEntity.ok(Map.of("status", status));
    }
}
