package com.aegis.util;

import java.util.ArrayList;
import java.util.List;

/** Splits harvest text into overlapping chunks for vector indexing. */
public final class NewsTextChunker {

    private NewsTextChunker() {}

    public static List<String> chunk(String title, String content, int chunkSize, int overlap, int maxChunks) {
        String body = join(title, content);
        if (body.isBlank()) {
            return List.of();
        }
        if (body.length() <= chunkSize) {
            return List.of(body.trim());
        }
        int step = Math.max(1, chunkSize - overlap);
        List<String> chunks = new ArrayList<>();
        for (int start = 0; start < body.length() && chunks.size() < maxChunks; start += step) {
            int end = Math.min(body.length(), start + chunkSize);
            String piece = body.substring(start, end).trim();
            if (!piece.isBlank()) {
                chunks.add(piece);
            }
            if (end >= body.length()) {
                break;
            }
        }
        return List.copyOf(chunks);
    }

    private static String join(String title, String content) {
        String t = title != null ? title.trim() : "";
        String c = content != null ? content.trim() : "";
        if (t.isBlank()) {
            return c;
        }
        if (c.isBlank()) {
            return t;
        }
        return t + "\n\n" + c;
    }
}
