<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { fetchCompetitorSummary, fetchFeed } from '@/composables/useInsightFeed'
import type { CompetitorInsightSummary, Insight, InsightCategory } from '@/types/insight'
import { categoryLabel } from '@/lib/categoryLabels'
import { sourceLabel } from '@/lib/insightLabels'
import type { SourceType } from '@/types/insight'
import ThreatCard from '@/components/ThreatCard.vue'

const PAGE = 50

const route = useRoute()
const router = useRouter()

const name = computed(() => String(route.params.name ?? ''))
const summary = ref<CompetitorInsightSummary | null>(null)
const insights = ref<Insight[]>([])
const feedTotal = ref(0)
const feedHasMore = ref(false)
const loading = ref(true)
const feedLoading = ref(false)

async function loadSummary() {
  summary.value = await fetchCompetitorSummary(name.value)
}

async function loadFeed(append = false) {
  feedLoading.value = true
  try {
    const offset = append ? insights.value.length : 0
    const page = await fetchFeed({
      competitor: name.value,
      limit: PAGE,
      offset,
      sort: 'threat',
    })
    if (append) {
      const seen = new Set(insights.value.map(i => i.id))
      for (const item of page.items) {
        if (!seen.has(item.id)) insights.value.push(item)
      }
    } else {
      insights.value = page.items
    }
    feedTotal.value = page.total
    feedHasMore.value = page.hasMore
  } catch {
    if (!append) insights.value = []
    feedHasMore.value = false
  } finally {
    feedLoading.value = false
  }
}

async function bootstrap() {
  loading.value = true
  try {
    await Promise.all([loadSummary(), loadFeed()])
  } catch {
    summary.value = null
    insights.value = []
  } finally {
    loading.value = false
  }
}

onMounted(() => { void bootstrap() })
watch(name, () => { void bootstrap() })

function back() {
  void router.push({ path: '/', query: { competitor: name.value } })
}

function catLabel(raw: string): string {
  return categoryLabel(raw as InsightCategory)
}
</script>

<template>
  <div class="min-h-screen bg-gray-950 text-gray-100">
    <header class="sticky top-0 z-10 border-b border-gray-800 bg-gray-950/90 px-4 py-3 backdrop-blur">
      <div class="mx-auto flex max-w-7xl items-center gap-4">
        <button type="button" class="text-sm text-gray-400 hover:text-white" @click="back">← Dashboard</button>
        <div>
          <h1 class="text-xl font-bold text-white">{{ name }}</h1>
          <p class="text-xs text-gray-500">Competitor intelligence</p>
        </div>
      </div>
    </header>

    <main class="mx-auto max-w-3xl px-4 py-6">
      <div v-if="loading" class="text-sm text-gray-500">Loading…</div>
      <template v-else-if="summary">
        <section class="mb-6 grid gap-3 sm:grid-cols-2">
          <div class="rounded-lg bg-gray-900 p-4 ring-1 ring-gray-800">
            <p class="text-[10px] uppercase text-gray-500">Insights</p>
            <p class="font-mono text-2xl text-white">{{ summary.totalInsights.toLocaleString() }}</p>
          </div>
          <div class="rounded-lg bg-gray-900 p-4 ring-1 ring-gray-800">
            <p class="text-[10px] uppercase text-gray-500">High threat (≥7)</p>
            <p class="font-mono text-2xl text-red-400">{{ summary.highThreatCount.toLocaleString() }}</p>
          </div>
        </section>

        <section v-if="summary.byCategory.length" class="mb-6 rounded-lg bg-gray-900/50 p-3 ring-1 ring-gray-800">
          <p class="mb-2 text-[10px] font-semibold uppercase text-gray-500">Category mix</p>
          <ul class="space-y-1 text-xs text-gray-300">
            <li v-for="c in summary.byCategory" :key="c.category" class="flex justify-between gap-2">
              <span>{{ catLabel(c.category) }}</span>
              <span class="font-mono text-gray-500">{{ c.count }}</span>
            </li>
          </ul>
        </section>

        <section v-if="summary.bySource.length" class="mb-6 rounded-lg bg-gray-900/50 p-3 ring-1 ring-gray-800">
          <p class="mb-2 text-[10px] font-semibold uppercase text-gray-500">Source mix</p>
          <ul class="space-y-1 text-xs text-gray-300">
            <li v-for="s in summary.bySource" :key="s.sourceType" class="flex justify-between gap-2">
              <span>{{ sourceLabel(s.sourceType as SourceType) }}</span>
              <span class="font-mono text-gray-500">{{ s.count }}</span>
            </li>
          </ul>
        </section>

        <div class="mb-3 flex items-center justify-between">
          <h2 class="text-xs font-semibold uppercase tracking-wider text-gray-500">
            Insights by threat
            <span class="ml-1 font-normal text-gray-600">({{ insights.length }} of {{ feedTotal.toLocaleString() }})</span>
          </h2>
        </div>

        <ThreatCard v-for="i in insights" :key="i.id" :insight="i" />

        <div v-if="feedHasMore" class="mt-4 flex justify-center">
          <button
            type="button"
            class="rounded-md bg-gray-800 px-4 py-2 text-sm text-gray-200 ring-1 ring-gray-700 hover:bg-gray-700 disabled:opacity-50"
            :disabled="feedLoading"
            @click="loadFeed(true)"
          >
            {{ feedLoading ? 'Loading…' : 'Load more' }}
          </button>
        </div>
      </template>
      <p v-else class="text-sm text-gray-500">No data for this competitor.</p>
    </main>
  </div>
</template>
