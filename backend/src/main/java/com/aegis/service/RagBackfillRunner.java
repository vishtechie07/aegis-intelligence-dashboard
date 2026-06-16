package com.aegis.service;

import com.aegis.config.RagProperties;
import com.aegis.repository.CompetitorNewsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "aegis.rag", name = "backfill-on-startup", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class RagBackfillRunner implements ApplicationRunner {

    private final RagProperties ragProperties;
    private final CompetitorNewsRepository newsRepository;
    private final RagIndexingService ragIndexingService;

    @Override
    public void run(ApplicationArguments args) {
        if (!ragProperties.enabled()) {
            log.warn("[RAG] backfill-on-startup ignored because aegis.rag.enabled=false");
            return;
        }
        var ids = newsRepository.findAllIds();
        log.info("[RAG] scheduling sequential backfill for {} articles", ids.size());
        ragIndexingService.runBackfill(ids);
    }
}
