package com.aegis.service;

import com.aegis.dto.SourceLastHarvest;
import com.aegis.repository.CompetitorNewsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HarvestActivityServiceTest {

    @Mock CompetitorNewsRepository newsRepository;

    HarvestActivityService service;

    @BeforeEach
    void setUp() {
        service = new HarvestActivityService(newsRepository);
    }

    @Test
    void snapshotIso_mergesDbAndMemoryTakingNewest() {
        var dbTime = OffsetDateTime.of(2026, 1, 1, 12, 0, 0, 0, ZoneOffset.UTC);
        when(newsRepository.findLatestHarvestBySource()).thenReturn(List.of(
                new SourceLastHarvest("RSS", dbTime),
                new SourceLastHarvest("GDELT", dbTime)));
        service.record("RSS");
        service.record("HACKERNEWS");

        Map<String, String> snapshot = service.snapshotIso();

        assertThat(snapshot).containsKeys("RSS", "GDELT", "HACKERNEWS");
        assertThat(Instant.parse(snapshot.get("RSS"))).isAfter(dbTime.toInstant());
        assertThat(snapshot.get("GDELT")).isEqualTo(dbTime.toInstant().toString());
    }
}
