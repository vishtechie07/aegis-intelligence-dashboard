package com.aegis.service;

import com.aegis.config.DynamicChatClientProvider;
import com.aegis.exception.DemoQuotaExceededException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DemoQuotaServiceTest {

    @Mock DynamicChatClientProvider provider;

    DemoQuotaService service;

    @BeforeEach
    void setUp() {
        service = new DemoQuotaService(provider, 5, true);
    }

    @Test
    void allowsInteractiveAiWhenUserKeySet() {
        when(provider.isServerKeyAvailable()).thenReturn(true);
        when(provider.isRuntimeKeySet("sess-1")).thenReturn(true);
        service.assertInteractiveAiAllowed("sess-1", "10.0.0.1");
    }

    @Test
    void blocksAfterTrialExpires() {
        when(provider.isServerKeyAvailable()).thenReturn(true);
        when(provider.isRuntimeKeySet("sess-2")).thenReturn(false);
        service.seedQuotaStart("sess-2", "10.0.0.2", Instant.now().minusSeconds(6 * 60));
        assertThatThrownBy(() -> service.assertInteractiveAiAllowed("sess-2", "10.0.0.2"))
                .isInstanceOf(DemoQuotaExceededException.class)
                .hasMessageContaining("Demo period ended");
    }

    @Test
    void newSessionSameIpStillBlockedAfterExpiry() {
        when(provider.isServerKeyAvailable()).thenReturn(true);
        when(provider.isRuntimeKeySet("sess-new")).thenReturn(false);
        service.seedQuotaStart("sess-old", "10.0.0.3", Instant.now().minusSeconds(6 * 60));
        assertThatThrownBy(() -> service.assertInteractiveAiAllowed("sess-new", "10.0.0.3"))
                .isInstanceOf(DemoQuotaExceededException.class);
    }

    @Test
    void status_reportsSecondsRemaining() {
        when(provider.isServerKeyAvailable()).thenReturn(true);
        when(provider.isRuntimeKeySet("sess-3")).thenReturn(false);
        service.resetQuota("sess-3", "10.0.0.4");
        var status = service.status("sess-3", "10.0.0.4");
        assertThat(status.trialEnabled()).isTrue();
        assertThat(status.usingHostedKey()).isTrue();
        assertThat(status.requiresUserKey()).isFalse();
        assertThat(status.secondsRemaining()).isGreaterThan(0);
    }
}
