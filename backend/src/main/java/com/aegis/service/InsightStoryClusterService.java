package com.aegis.service;

import com.aegis.dto.InsightEvent;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class InsightStoryClusterService {

    private static final int MIN_SHARED_TOKENS = 3;

    public List<InsightEvent> assignClusters(List<InsightEvent> items) {
        if (items == null || items.size() < 2) {
            return items == null ? List.of() : items;
        }
        int n = items.size();
        int[] parent = new int[n];
        for (int i = 0; i < n; i++) parent[i] = i;

        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                if (shouldCluster(items.get(i), items.get(j))) {
                    union(parent, i, j);
                }
            }
        }

        int[] sizes = new int[n];
        for (int i = 0; i < n; i++) {
            sizes[find(parent, i)]++;
        }

        List<InsightEvent> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            InsightEvent item = items.get(i);
            int root = find(parent, i);
            String key = sizes[root] > 1 ? clusterKey(item.competitorName(), root) : null;
            out.add(withClusterKey(item, key));
        }
        return out;
    }

    private static boolean shouldCluster(InsightEvent a, InsightEvent b) {
        if (a.competitorName() == null || b.competitorName() == null) return false;
        if (!a.competitorName().equalsIgnoreCase(b.competitorName())) return false;
        Set<String> ta = tokens(a.title());
        Set<String> tb = tokens(b.title());
        if (ta.isEmpty() || tb.isEmpty()) return false;
        int shared = 0;
        for (String t : ta) {
            if (tb.contains(t) && ++shared >= MIN_SHARED_TOKENS) return true;
        }
        return false;
    }

    private static Set<String> tokens(String title) {
        Set<String> out = new HashSet<>();
        if (title == null) return out;
        for (String raw : title.toLowerCase(Locale.ROOT).split("\\W+")) {
            if (raw.length() >= 4) out.add(raw);
        }
        return out;
    }

    private static String clusterKey(String competitor, int root) {
        String c = competitor != null ? competitor.toLowerCase(Locale.ROOT).replaceAll("\\W+", "-") : "unknown";
        return c + "-" + root;
    }

    private static InsightEvent withClusterKey(InsightEvent item, String clusterKey) {
        return new InsightEvent(
                item.id(), item.newsId(), item.competitorName(), item.title(), item.sourceUrl(),
                item.sourceType(), item.agentName(), item.category(), item.threatLevel(),
                item.summary(), item.strategicAdvice(), item.publishedAt(), item.processedAt(),
                item.contentExcerpt(), item.ragAvailable(), clusterKey);
    }

    private static int find(int[] parent, int x) {
        if (parent[x] != x) parent[x] = find(parent, parent[x]);
        return parent[x];
    }

    private static void union(int[] parent, int a, int b) {
        int ra = find(parent, a);
        int rb = find(parent, b);
        if (ra != rb) parent[rb] = ra;
    }
}
