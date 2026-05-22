package com.aegis.service;

import com.aegis.exception.RateLimitExceededException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InteractiveAiRateLimiterTest {

    @Test
    void blocksWhenLimitExceeded() {
        InteractiveAiRateLimiter limiter = new InteractiveAiRateLimiter(2);
        limiter.assertAllowed("quota-1");
        limiter.assertAllowed("quota-1");
        assertThatThrownBy(() -> limiter.assertAllowed("quota-1"))
                .isInstanceOf(RateLimitExceededException.class);
    }

    @Test
    void separateKeysHaveSeparateWindows() {
        InteractiveAiRateLimiter limiter = new InteractiveAiRateLimiter(1);
        limiter.assertAllowed("a");
        assertThatCode(() -> limiter.assertAllowed("b")).doesNotThrowAnyException();
    }
}
