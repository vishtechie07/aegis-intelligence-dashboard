package com.aegis.controller;

import com.aegis.config.DynamicChatClientProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.mockito.Mockito.*;

@WebFluxTest(SettingsController.class)
@TestPropertySource(properties = "spring.ai.openai.api-key=sk-test-placeholder")
@SuppressWarnings({"null", "DataFlowIssue"})
class SettingsControllerTest {

    @Autowired WebTestClient client;
    @MockitoBean DynamicChatClientProvider provider;

    @Test
    void getStatus_returnsConfiguredFalseByDefault() {
        when(provider.isConfigured()).thenReturn(false);
        when(provider.isRuntimeKeySet()).thenReturn(false);
        when(provider.isServerKeyAvailable()).thenReturn(false);

        client.get().uri("/api/settings/status")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.configured").isEqualTo(false)
                .jsonPath("$.runtimeKeySet").isEqualTo(false)
                .jsonPath("$.serverKeyAvailable").isEqualTo(false);
    }

    @Test
    void getStatus_returnsConfiguredTrueWhenKeySet() {
        when(provider.isConfigured()).thenReturn(true);
        when(provider.isRuntimeKeySet()).thenReturn(true);
        when(provider.isServerKeyAvailable()).thenReturn(true);

        client.get().uri("/api/settings/status")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.configured").isEqualTo(true)
                .jsonPath("$.runtimeKeySet").isEqualTo(true);
    }

    @Test
    void updateKey_acceptsValidKey() {
        client.put().uri("/api/settings/openai-key")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"apiKey": "sk-proj-abc123def456ghi789jkl012mno345"}
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isNotEmpty();

        verify(provider).updateKey("sk-proj-abc123def456ghi789jkl012mno345");
    }

    @Test
    void updateKey_rejectsBlankKey() {
        client.put().uri("/api/settings/openai-key")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"apiKey": ""}
                        """)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.error").isEqualTo("API key must not be blank");

        verify(provider, never()).updateKey(any());
    }

    @Test
    void updateKey_rejectsInvalidFormat() {
        client.put().uri("/api/settings/openai-key")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"apiKey": "not-an-openai-key"}
                        """)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.error").isEqualTo("Invalid OpenAI API key format");

        verify(provider, never()).updateKey(any());
    }

    @Test
    void updateKey_rejectsNullKey() {
        client.put().uri("/api/settings/openai-key")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"apiKey": null}
                        """)
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void clearKey_revertsToServerKey() {
        when(provider.isConfigured()).thenReturn(true);

        client.delete().uri("/api/settings/openai-key")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isNotEmpty();

        verify(provider).clearRuntimeKey();
    }
}
