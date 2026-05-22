package com.aegis.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DeepDiveRelevanceGuardTest {

    @Test
    void detectsSimpleMath() {
        assertThat(DeepDiveRelevanceGuard.isObviouslyOffTopic("1+1")).isTrue();
        assertThat(DeepDiveRelevanceGuard.isObviouslyOffTopic(" 2 * 3 ")).isTrue();
    }

    @Test
    void allowsStrategicQuestions() {
        assertThat(DeepDiveRelevanceGuard.isObviouslyOffTopic(
                "How does this affect our enterprise pricing strategy?")).isFalse();
    }

    @Test
    void offTopicResponseContainsGuidance() {
        assertThat(DeepDiveRelevanceGuard.offTopicResponse()).contains("strategic questions");
    }
}
