package com.aegis.harvester;

import com.aegis.dto.NewsArticle;
import com.aegis.entity.CompetitorNews;
import com.aegis.repository.CompetitorNewsRepository;
import com.aegis.service.AgentOrchestrationService;
import com.aegis.util.NewsTextSanitizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Objects;

import java.time.OffsetDateTime;

/** Save + dedupe by URL, persist, then run agent pipeline. */
@Component
@RequiredArgsConstructor
@Slf4j
@SuppressWarnings("null")
public class HarvesterSupport {

    private final CompetitorNewsRepository newsRepository;
    private final AgentOrchestrationService orchestrationService;

    /** true if new and dispatched, false if duplicate. */
    public boolean saveAndDispatch(String competitor, String title, String content,
                                   String url, OffsetDateTime publishedAt, String sourceType) {
        if (url == null || title == null) return false;
        if (newsRepository.existsBySourceUrl(url)) return false;

        String cleanTitle = NewsTextSanitizer.stripHtml(title);
        String cleanContent = NewsTextSanitizer.stripHtml(content);
        if (cleanTitle.isBlank()) return false;

        CompetitorNews saved = newsRepository.save(
                CompetitorNews.builder()
                        .competitorName(competitor)
                        .title(cleanTitle)
                        .content(cleanContent)
                        .sourceUrl(url)
                        .publishedAt(publishedAt)
                        .sourceType(sourceType)
                        .build()
        );

        Long newsId = saved.getId();
        log.debug("[{}] Saved #{} '{}' for {}", sourceType, newsId, title, competitor);
        NewsArticle article = new NewsArticle(
                Objects.requireNonNullElse(saved.getCompetitorName(), ""),
                Objects.requireNonNullElse(saved.getTitle(), ""),
                saved.getContent(),
                Objects.requireNonNullElse(saved.getSourceUrl(), ""),
                saved.getPublishedAt());
        orchestrationService.processAsync(article, newsId);
        return true;
    }
}
