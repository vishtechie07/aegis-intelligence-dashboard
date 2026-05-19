package com.aegis.controller;

import com.aegis.config.DynamicChatClientProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
public class SettingsController {

    private final DynamicChatClientProvider provider;

    record KeyRequest(String apiKey) {}
    record StatusResponse(boolean configured, boolean runtimeKeySet, boolean serverKeyAvailable) {}

    @GetMapping("/status")
    public StatusResponse status() {
        return new StatusResponse(
                provider.isConfigured(),
                provider.isRuntimeKeySet(),
                provider.isServerKeyAvailable());
    }

    @PutMapping("/openai-key")
    public ResponseEntity<Map<String, String>> updateKey(@RequestBody KeyRequest req) {
        if (req.apiKey() == null || req.apiKey().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "API key must not be blank"));
        }
        if (!req.apiKey().startsWith("sk-")) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid OpenAI API key format"));
        }
        if (DynamicChatClientProvider.isPlaceholderKey(req.apiKey())) {
            return ResponseEntity.ok(Map.of("status", "Use a real API key from platform.openai.com to activate AI agents."));
        }
        provider.updateKey(req.apiKey());
        return ResponseEntity.ok(Map.of("status", "Key accepted — AI agents are now active"));
    }

    @DeleteMapping("/openai-key")
    public ResponseEntity<Map<String, String>> clearKey() {
        provider.clearRuntimeKey();
        String status = provider.isConfigured()
                ? "Reverted to server default API key"
                : "API key cleared — set OPENAI_API_KEY on the server or add a key in Settings";
        return ResponseEntity.ok(Map.of("status", status));
    }
}
