package com.aegis.service;

import com.aegis.dto.InsightEvent;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InsightStoryClusterServiceTest {

    private final InsightStoryClusterService service = new InsightStoryClusterService();

    @Test
    void assignClusters_groupsSimilarTitles() {
        InsightEvent a = event(1L, "OpenAI", "OpenAI launches GPT-5 flagship model today");
        InsightEvent b = event(2L, "OpenAI", "OpenAI GPT-5 flagship model announced for enterprise");
        InsightEvent c = event(3L, "Google", "Google unveils new cloud region");

        List<InsightEvent> out = service.assignClusters(List.of(a, b, c));

        assertThat(out.get(0).clusterKey()).isNotNull();
        assertThat(out.get(0).clusterKey()).isEqualTo(out.get(1).clusterKey());
        assertThat(out.get(2).clusterKey()).isNull();
    }

    private static InsightEvent event(Long id, String competitor, String title) {
        return new InsightEvent(id, id * 10, competitor, title, null, "RSS", "Strategist",
                "PRODUCT_LAUNCH", 7, "s", "a", OffsetDateTime.now(), OffsetDateTime.now(),
                "excerpt", false, null);
    }
}
