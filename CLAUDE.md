# CLAUDE.md — Aegis Intelligence Engine

Coding standards and agent handoff protocols for this project.

## Architecture

```
backend/   Spring Boot 3.4 + WebFlux + Spring AI (Java 21)
frontend/  Vue 3 + Vite + TypeScript + Tailwind CSS
postgres   PostgreSQL 16 (Docker)
```

## Java Backend Standards

- **Records for DTOs** — `InsightEvent`, `InsightFeedPage`, `InsightStats`, `NewsArticle`, `DeepDiveRequest`, etc. Never use classes for data transfer.
- **Entities** — Use `@Data @Builder @NoArgsConstructor @AllArgsConstructor` (Lombok). Never use `@Data` on entities with bidirectional relations without `@EqualsAndHashCode(exclude=...)`.
- **Constructor injection** — Never `@Autowired` field injection.
- **Reactive** — All HTTP client calls use `WebClient`. Never `RestTemplate`. SSE endpoint returns `Flux<ServerSentEvent<T>>`.
- **Async orchestration** — `@Async` on `AgentOrchestrationService.processAsync`. Do not block the harvester thread.
- **Spring AI** — Use `ChatClient` fluent API exclusively. Never `ChatModel.call()` directly.
- **Error handling** — Agents must never throw; all catch blocks return a safe fallback value.
- **Tests** — Use JUnit 5 + AssertJ. Mock `ChatClient` in agent unit tests via `@MockitoBean`.

## TypeScript Frontend Standards

- **`<script setup lang="ts">`** — No Options API.
- **Type-safe DTOs** — `src/types/insight.ts` interfaces must exactly mirror Java records. Any backend change requires a matching frontend type update.
- **Pinia stores** — Composition API style (`defineStore(() => {})`). No Options API stores.
- **`v-memo`** on `ThreatCard` — Only re-render when `id`, `isNew`, or `threatLevel` changes.
- **Composables** — SSE in `useSse.ts`; feed fetch/filters in `useInsightFeed.ts` + `useFeedFilters.ts`; grouping in `useFeedGrouping.ts`. No raw `EventSource` in components.
- **Feed UI** — `InsightFeed.vue` orchestrates `InsightFeedSidebar` + `InsightFeedList`. Read/star/dismiss in `insightStore` (localStorage).
- **Labels** — `src/lib/insightLabels.ts` (threat tiers, source labels); `src/lib/categoryLabels.ts`.
- **No `any`** — TypeScript strict mode is on. Use `unknown` + type guards when necessary.

## Agent Handoff Protocol

The three-stage pipeline runs asynchronously after each harvested article:

```
NewsHarvester.harvest()
  └─ newsRepository.save(article)
  └─ orchestrationService.processAsync(article, newsId)   ← @Async
       └─ NoiseCancelerAgent.isRelevant()   → false → discard
       └─ MarketAnalystAgent.categorize()   → Category enum
       └─ StrategistAgent.analyze()         → {threatLevel, summary, strategicAdvice}
       └─ agentInsightRepository.save()
       └─ insightService.publish(event)     → SSE hot publisher
            └─ Vue EventSource → store.addInsight() → ThreatCard renders
```

Rules:
- Each agent is a standalone `@Service` — no direct coupling between agents.
- If an agent fails, the pipeline continues with a safe default (never fails the chain).
- The `Sinks.Many` publisher is the single source of truth for real-time delivery.

## Docker

- `docker-compose.yml` is the canonical run target.
- Optional `docker-compose.override.yml` (gitignored; see `docker-compose.override.yml.example`) for Postgres port 5434.
- Always use `docker compose up --build` for a clean rebuild.
- The backend waits for the Postgres `healthcheck` before starting (prevents Flyway race condition).
- Never hardcode credentials — use `.env` file (copy from `.env.example`).

## Environment Variables

| Variable | Required | Description |
|---|---|---|
| `NEWSAPI_KEY` | Yes | NewsAPI.org key |
| `OPENAI_API_KEY` | Yes | OpenAI API key for Spring AI agents |
| `POSTGRES_PASSWORD` | Yes | PostgreSQL password |
| `TRACKED_COMPETITORS` | No | Comma-separated list (default: Microsoft,Google,Amazon,OpenAI,Anthropic) |

## Self-Healing Pattern

If a `401` error occurs in the harvester (invalid API key), the error is logged at `WARN` level with the key name. The `NewsHarvester` skips the cycle and retries on the next cron tick. Monitor via `/actuator/health`.
