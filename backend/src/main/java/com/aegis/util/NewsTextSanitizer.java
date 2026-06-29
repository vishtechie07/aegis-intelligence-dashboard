package com.aegis.util;

/** Strips RSS/HTML noise from harvested titles and body text. */
public final class NewsTextSanitizer {

    private NewsTextSanitizer() {}

    public static String stripHtml(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String s = raw;
        s = s.replaceAll("(?is)<script[^>]*>.*?</script>", " ");
        s = s.replaceAll("(?is)<style[^>]*>.*?</style>", " ");
        s = s.replaceAll("<[^>]+>", " ");
        s = decodeBasicEntities(s);
        return normalizeWhitespace(s);
    }

    public static String buildExcerpt(String title, String content, int maxLen) {
        String t = stripHtml(title);
        String c = stripHtml(content);
        String body;
        if (c.isBlank()) {
            body = t;
        } else if (t.isBlank()) {
            body = c;
        } else if (equalsIgnoreCaseNormalized(t, c) || c.equalsIgnoreCase(t)) {
            body = t;
        } else if (c.regionMatches(true, 0, t, 0, t.length()) && c.length() - t.length() < 40) {
            body = t;
        } else if (t.regionMatches(true, 0, c, 0, c.length()) && t.length() - c.length() < 40) {
            body = t;
        } else if (looksLikeUrlOnly(c)) {
            body = t;
        } else {
            body = t + "\n\n" + c;
        }
        if (body.isBlank()) {
            return "";
        }
        int cap = Math.max(1, maxLen);
        return body.length() > cap ? body.substring(0, cap) + "…" : body;
    }

    private static boolean looksLikeUrlOnly(String text) {
        String s = text.trim();
        return s.startsWith("http://") || s.startsWith("https://") || s.contains("news.google.com/rss/articles");
    }

    private static boolean equalsIgnoreCaseNormalized(String a, String b) {
        return normalizeWhitespace(a).equalsIgnoreCase(normalizeWhitespace(b));
    }

    private static String decodeBasicEntities(String s) {
        return s
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&apos;", "'");
    }

    private static String normalizeWhitespace(String s) {
        return s.replaceAll("\\s+", " ").trim();
    }
}
