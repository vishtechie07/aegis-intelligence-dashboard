package com.aegis.config;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class DynamicChatClientProviderTest {

    @Test
    void notConfigured_whenEnvKeyBlankAndNoModel() {
        DynamicChatClientProvider provider = new DynamicChatClientProvider("", null);

        assertThat(provider.isConfigured()).isFalse();
        assertThat(provider.isRuntimeKeySet()).isFalse();
    }

    @Test
    void configured_whenEnvKeyPresentAndModelProvided() {
        ChatModel model = mock(ChatModel.class);
        DynamicChatClientProvider provider = new DynamicChatClientProvider("sk-env-key", model);

        assertThat(provider.isConfigured()).isTrue();
        assertThat(provider.isRuntimeKeySet()).isFalse();
    }

    @Test
    void get_throwsApiKeyNotConfiguredException_whenNotConfigured() {
        DynamicChatClientProvider provider = new DynamicChatClientProvider("", null);

        assertThatThrownBy(provider::get)
                .isInstanceOf(ApiKeyNotConfiguredException.class)
                .hasMessageContaining("OpenAI API key not configured");
    }

    @Test
    void updateKey_makesProviderConfigured() {
        DynamicChatClientProvider provider = new DynamicChatClientProvider("", null);
        assertThat(provider.isConfigured()).isFalse();

        provider.updateKey("sk-proj-testkey1234567890");

        assertThat(provider.isConfigured()).isTrue();
        assertThat(provider.isRuntimeKeySet()).isTrue();
        assertThat(provider.get()).isNotNull();
    }

    @Test
    void updateKey_replacesExistingClient() {
        ChatModel model = mock(ChatModel.class);
        DynamicChatClientProvider provider = new DynamicChatClientProvider("sk-old", model);
        var originalClient = provider.get();

        provider.updateKey("sk-proj-newkey1234567890");

        assertThat(provider.get()).isNotSameAs(originalClient);
        assertThat(provider.isRuntimeKeySet()).isTrue();
    }

    @Test
    void clearRuntimeKey_revertsToEnvironmentClient() {
        ChatModel model = mock(ChatModel.class);
        DynamicChatClientProvider provider = new DynamicChatClientProvider("sk-env-key", model);
        var envClient = provider.get();

        provider.updateKey("sk-proj-newkey1234567890");
        assertThat(provider.isRuntimeKeySet()).isTrue();

        provider.clearRuntimeKey();

        assertThat(provider.isRuntimeKeySet()).isFalse();
        assertThat(provider.isConfigured()).isTrue();
        assertThat(provider.isServerKeyAvailable()).isTrue();
        assertThat(provider.get()).isSameAs(envClient);
    }

    @Test
    void clearRuntimeKey_unconfiguredWhenNoEnvironmentKey() {
        DynamicChatClientProvider provider = new DynamicChatClientProvider("", null);
        provider.updateKey("sk-proj-newkey1234567890");
        assertThat(provider.isConfigured()).isTrue();

        provider.clearRuntimeKey();

        assertThat(provider.isConfigured()).isFalse();
        assertThat(provider.isRuntimeKeySet()).isFalse();
    }
}
