package com.aegis.service;

import com.aegis.config.DynamicChatClientProvider;
import com.aegis.dto.CompetitorDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class CompetitorService {

    private record Meta(String githubOrg, String ticker, String industry, String country) {}

    private final Map<String, Meta> metaMap = new ConcurrentHashMap<>();
    private final List<String> orderedNames = new ArrayList<>();
    private final DynamicChatClientProvider chatClientProvider;
    private final DemoQuotaService demoQuotaService;
    private final InteractiveAiRateLimiter rateLimiter;
    private final CompetitorMutationPolicy mutationPolicy;

    public CompetitorService(
            @Value("${aegis.harvest.competitors:Microsoft,Google,Amazon,OpenAI,Anthropic}") String competitorsRaw,
            @Value("#{${aegis.sources.github.org-map:{}}}") Map<String, String> configOrgMap,
            DynamicChatClientProvider chatClientProvider,
            DemoQuotaService demoQuotaService,
            InteractiveAiRateLimiter rateLimiter,
            CompetitorMutationPolicy mutationPolicy) {
        this.chatClientProvider = chatClientProvider;
        this.demoQuotaService = demoQuotaService;
        this.rateLimiter = rateLimiter;
        this.mutationPolicy = mutationPolicy;
        Map<String, String> orgMap = configOrgMap != null ? configOrgMap : Map.of();
        String raw = competitorsRaw != null ? competitorsRaw : "";
        Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .forEach(name -> {
                    orderedNames.add(name);
                    metaMap.put(name, new Meta(
                            orgMap.getOrDefault(name, name.toLowerCase()),
                            null, null, null));
                });
        log.info("CompetitorService initialised with: {}", orderedNames);
    }

    public synchronized List<CompetitorDto> getAll() {
        return orderedNames.stream().map(this::toDto).toList();
    }

    public synchronized List<String> getNames() {
        return List.copyOf(orderedNames);
    }

    public String getGithubOrg(String name) {
        Meta m = metaMap.get(name);
        return m != null && m.githubOrg() != null ? m.githubOrg() : name.toLowerCase();
    }

    public String getTicker(String name) {
        Meta m = metaMap.get(name);
        return m != null ? m.ticker() : null;
    }

    /** Competitors with ticker set. */
    public synchronized List<CompetitorDto> getWithTicker() {
        return orderedNames.stream()
                .map(this::toDto)
                .filter(d -> d.ticker() != null && !d.ticker().isBlank())
                .toList();
    }

    public synchronized void add(CompetitorDto dto) {
        mutationPolicy.assertMutationsAllowed();
        String name = dto.name().trim();
        if (orderedNames.stream().anyMatch(n -> n.equalsIgnoreCase(name))) return;
        String org = dto.githubOrg() != null && !dto.githubOrg().isBlank()
                ? dto.githubOrg().trim()
                : name.toLowerCase().replaceAll("\\s+", "-");
        orderedNames.add(name);
        metaMap.put(name, new Meta(org, trimOrNull(dto.ticker()), trimOrNull(dto.industry()), trimOrNull(dto.country())));
        log.info("Competitor added: {} (github:{}, ticker:{}, industry:{}, country:{})",
                name, org, dto.ticker(), dto.industry(), dto.country());
    }

    public synchronized boolean remove(String name) {
        mutationPolicy.assertMutationsAllowed();
        boolean removed = orderedNames.removeIf(n -> n.equalsIgnoreCase(name));
        if (removed) metaMap.entrySet().removeIf(e -> e.getKey().equalsIgnoreCase(name));
        return removed;
    }

    @SuppressWarnings("null")
    public CompetitorDto lookup(String companyName, String countryHint, String sessionId, String clientIp) {
        demoQuotaService.assertInteractiveAiAllowed(sessionId, clientIp);
        rateLimiter.assertAllowed(demoQuotaService.resolveQuotaKey(sessionId, clientIp));
        String safeName = companyName != null ? companyName.trim() : "";
        if (safeName.length() > 200) {
            safeName = safeName.substring(0, 200);
        }
        String safeCountry = countryHint != null ? countryHint : "unknown";
        try {
            ChatClient client = chatClientProvider.getForSession(sessionId);
            String prompt = String.format("""
                    You are a business intelligence assistant. Given a company name and optional country,
                    return ONLY a valid JSON object with these exact fields:
                      "name": canonical company name,
                      "githubOrg": GitHub organisation slug (lowercase, best guess),
                      "ticker": stock ticker symbol if publicly traded (e.g. MSFT), or "" if private,
                      "industry": one of: tech, finance, defence, agriculture, textiles, healthcare, energy, retail, media, other,
                      "description": one sentence about what the company does,
                      "country": full country name
                    
                    Company: %s
                    Country hint: %s
                    
                    Respond with only the JSON object, no markdown fences.
                    """, safeName, safeCountry);

            Object raw = client.prompt(prompt).call().content();
            String json = raw == null ? "{}" : Objects.requireNonNullElse(raw.toString(), "{}");
            return parseJsonDto(json, safeName, safeCountry);
        } catch (Exception e) {
            log.warn("AI lookup failed for {}: {}", safeName, e.getMessage());
            String slug = safeName.toLowerCase().replaceAll("\\s+", "-");
            return new CompetitorDto(safeName, slug, null, null, null, safeCountry);
        }
    }

    private CompetitorDto parseJsonDto(String json, String fallbackName, String fallbackCountry) {
        try {
            String cleaned = json.replaceAll("(?s)```[a-z]*\\n?|```", "").trim();
            return new CompetitorDto(
                    coalesce(extractJsonString(cleaned, "name"), fallbackName),
                    coalesce(extractJsonString(cleaned, "githubOrg"), fallbackName.toLowerCase()),
                    extractJsonString(cleaned, "description"),
                    extractJsonString(cleaned, "ticker"),
                    extractJsonString(cleaned, "industry"),
                    coalesce(extractJsonString(cleaned, "country"), fallbackCountry));
        } catch (Exception e) {
            return new CompetitorDto(fallbackName, fallbackName.toLowerCase(), null, null, null, fallbackCountry);
        }
    }

    private CompetitorDto toDto(String name) {
        Meta m = metaMap.getOrDefault(name, new Meta(name.toLowerCase(), null, null, null));
        return new CompetitorDto(name, m.githubOrg(), null, m.ticker(), m.industry(), m.country());
    }

    private String extractJsonString(String json, String key) {
        if (json == null) return null;
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("\"" + key + "\"\\s*:\\s*\"([^\"]*)\"")
                .matcher(json);
        if (!m.find()) return null;
        String g = m.group(1);
        return (g == null || g.isBlank()) ? null : g;
    }

    private String trimOrNull(String s) {
        return s != null && !s.isBlank() ? s.trim() : null;
    }

    private String coalesce(String a, String b) {
        return a != null ? a : b;
    }
}
