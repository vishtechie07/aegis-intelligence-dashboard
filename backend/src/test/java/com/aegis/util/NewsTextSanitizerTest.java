package com.aegis.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NewsTextSanitizerTest {

    @Test
    void stripHtml_removesAnchorTags() {
        String html = "<a href=\"https://news.google.com/rss/articles/CBMiLong\">OpenAI chip news</a>";
        assertThat(NewsTextSanitizer.stripHtml(html)).isEqualTo("OpenAI chip news");
    }

    @Test
    void buildExcerpt_dedupesTitleWrappedInHtml() {
        String title = "Tech Bytes: OpenAI and Broadcom unveil Jalapeño chip";
        String content = "<a href=\"https://news.google.com/rss/articles/ABC\">"
                + title + "</a>";
        String excerpt = NewsTextSanitizer.buildExcerpt(title, content, 400);
        assertThat(excerpt).isEqualTo(title);
        assertThat(excerpt).doesNotContain("<");
        assertThat(excerpt).doesNotContain("news.google.com");
    }

    @Test
    void buildExcerpt_keepsDistinctBody() {
        String title = "Big announcement";
        String content = "Article body with extra detail.";
        assertThat(NewsTextSanitizer.buildExcerpt(title, content, 400))
                .isEqualTo("Big announcement\n\nArticle body with extra detail.");
    }

    @Test
    void buildExcerpt_truncates() {
        String body = "x".repeat(500);
        assertThat(NewsTextSanitizer.buildExcerpt(body, "", 100)).hasSize(101).endsWith("…");
    }
}
