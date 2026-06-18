package com.aegis.controller;

import com.aegis.config.DynamicChatClientProvider;
import com.aegis.dto.DemoQuotaStatus;
import com.aegis.service.DemoQuotaService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@WebFluxTest(SettingsController.class)
@TestPropertySource(properties = "spring.ai.openai.api-key=sk-test-placeholder")
@SuppressWarnings({"null", "DataFlowIssue"})
class SettingsControllerTest {

    private static final String SESSION = "11111111-2222-4333-8444-555555555555";

    @Autowired WebTestClient client;
    @MockitoBean DynamicChatClientProvider provider;
    @MockitoBean DemoQuotaService demoQuotaService;

    @Test
    void getStatus_returnsConfiguredFalseByDefault() {
        when(provider.isConfiguredForSession(SESSION)).thenReturn(false);
        when(provider.isRuntimeKeySet(SESSION)).thenReturn(false);
        when(provider.isServerKeyAvailable()).thenReturn(false);
        when(demoQuotaService.status(eq(SESSION), anyString())).thenReturn(
                new DemoQuotaStatus(false, false, false, 5, 0, 5, 5));

        client.get().uri("/api/settings/status")
                .header(DemoQuotaService.SESSION_HEADER, SESSION)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.configured").isEqualTo(false)
                .jsonPath("$.runtimeKeySet").isEqualTo(false);
    }

    @Test
    void updateKey_acceptsValidKey() {
        client.put().uri("/api/settings/openai-key")
                .header(DemoQuotaService.SESSION_HEADER, SESSION)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"apiKey": "sk-proj-abc123def456ghi789jkl012mno345"}
                        """)
                .exchange()
                .expectStatus().isOk();

        verify(provider).updateKey(SESSION, "sk-proj-abc123def456ghi789jkl012mno345");
    }

    @Test
    void updateKey_requiresSessionHeader() {
        client.put().uri("/api/settings/openai-key")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"apiKey": "sk-proj-abc123def456ghi789jkl012mno345"}
                        """)
                .exchange()
                .expectStatus().isBadRequest();

        verify(provider, never()).updateKey(anyString(), anyString());
    }

    @Test
    void updateKey_rejectsBlankKey() {
        client.put().uri("/api/settings/openai-key")
                .header(DemoQuotaService.SESSION_HEADER, SESSION)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"apiKey": ""}
                        """)
                .exchange()
                .expectStatus().isBadRequest();

        verify(provider, never()).updateKey(anyString(), anyString());
    }

    @Test
    void clearKey_requiresSessionHeader() {
        client.delete().uri("/api/settings/openai-key")
                .exchange()
                .expectStatus().isBadRequest();

        verify(provider, never()).clearRuntimeKey(anyString());
    }

    @Test
    void clearKey_clearsSessionKey() {
        when(provider.isConfiguredForSession(SESSION)).thenReturn(true);

        client.delete().uri("/api/settings/openai-key")
                .header(DemoQuotaService.SESSION_HEADER, SESSION)
                .exchange()
                .expectStatus().isOk();

        verify(provider).clearRuntimeKey(SESSION);
    }
}
