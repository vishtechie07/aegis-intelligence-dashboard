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

        assertThat(provider.isServerKeyAvailable()).isFalse();
        assertThat(provider.isConfiguredForSession("sess-a")).isFalse();
        assertThat(provider.isRuntimeKeySet("sess-a")).isFalse();
    }

    @Test
    void configured_whenEnvKeyPresentAndModelProvided() {
        ChatModel model = mock(ChatModel.class);
        DynamicChatClientProvider provider = new DynamicChatClientProvider("sk-env-key", model);

        assertThat(provider.isConfiguredForSession("sess-a")).isTrue();
        assertThat(provider.isRuntimeKeySet("sess-a")).isFalse();
    }

    @Test
    void get_throwsApiKeyNotConfiguredException_whenNotConfigured() {
        DynamicChatClientProvider provider = new DynamicChatClientProvider("", null);

        assertThatThrownBy(provider::get)
                .isInstanceOf(ApiKeyNotConfiguredException.class)
                .hasMessageContaining("OpenAI API key not configured");
    }

    @Test
    void updateKey_scopedToSession() {
        DynamicChatClientProvider provider = new DynamicChatClientProvider("", null);

        provider.updateKey("sess-a", "sk-proj-testkey1234567890");

        assertThat(provider.isRuntimeKeySet("sess-a")).isTrue();
        assertThat(provider.isRuntimeKeySet("sess-b")).isFalse();
        assertThatThrownBy(provider::get).isInstanceOf(ApiKeyNotConfiguredException.class);
        assertThat(provider.getForSession("sess-a")).isNotNull();
    }

    @Test
    void clearRuntimeKey_onlyClearsTargetSession() {
        ChatModel model = mock(ChatModel.class);
        DynamicChatClientProvider provider = new DynamicChatClientProvider("sk-env-key", model);

        provider.updateKey("sess-a", "sk-proj-newkey1234567890");
        assertThat(provider.isRuntimeKeySet("sess-a")).isTrue();

        provider.clearRuntimeKey("sess-a");

        assertThat(provider.isRuntimeKeySet("sess-a")).isFalse();
        assertThat(provider.getForSession("sess-a")).isSameAs(provider.get());
    }

    @Test
    void getForSession_fallsBackToEnvironmentClient() {
        ChatModel model = mock(ChatModel.class);
        DynamicChatClientProvider provider = new DynamicChatClientProvider("sk-env-key", model);

        assertThat(provider.getForSession("sess-x")).isSameAs(provider.get());
    }
}
