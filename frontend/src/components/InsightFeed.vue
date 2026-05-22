<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useInsightStore } from '@/stores/insightStore'
import { useSettingsStore } from '@/stores/settingsStore'
import type { Insight, InsightCategory } from '@/types/insight'
import ThreatCard from './ThreatCard.vue'

const store = useInsightStore()
const settings = useSettingsStore()
const route = useRoute()
const skeletonSlots = [1, 2, 3, 4]

const emptyFeedMessage = computed(() => {
  if (!settings.isConfigured) {
    return 'AI agents are inactive. News is harvested; analysis waits for an OpenAI key in Settings.'
  }
  if (store.connectionStatus === 'connected') {
    return 'Live — no insights yet. Harvesters will populate the feed shortly.'
  }
  if (store.connectionStatus === 'connecting') {
    return 'Connecting to live feed…'
  }
  return 'Reconnecting to live feed…'
})
const router = useRouter()

type ThreatFilter = 'all' | 'high'
type DateScope = 'all' | 'day'

const threatFilter = ref<ThreatFilter>('all')
const competitorFilter = ref<string>('all')
const categoryFilter = ref<InsightCategory | 'all'>('all')
const dateScope = ref<DateScope>('all')
const selectedDate = ref(todayLocal())

const maxCards = ref(60)
let syncingFromRoute = false

const allCategories: InsightCategory[] = [
  'PRODUCT_LAUNCH',
  'HIRING',
  'FINANCIAL_MOVE',
  'PARTNERSHIP',
  'LEGAL',
  'LEADERSHIP_CHANGE',
  'OTHER',
]

function qStr(v: unknown): string | undefined {
  if (typeof v === 'string') return v
  if (Array.isArray(v) && typeof v[0] === 'string') return v[0]
  return undefined
}

function categoryLabel(c: InsightCategory): string {
  const map: Record<InsightCategory, string> = {
    PRODUCT_LAUNCH: 'Product Launch',
    HIRING: 'Hiring',
    FINANCIAL_MOVE: 'Financial',
    PARTNERSHIP: 'Partnership',
    LEGAL: 'Legal',
    LEADERSHIP_CHANGE: 'Leadership',
    OTHER: 'Other',
  }
  return map[c]
}

function todayLocal(): string {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

function insightSortIso(i: Insight): string {
  return i.publishedAt || i.processedAt || ''
}

function dayKeyLocal(iso: string): string {
  if (!iso) return 'unknown'
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return 'unknown'
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

function formatDayHeading(ymd: string): string {
  if (ymd === 'unknown') return 'Unknown date'
  const [y, m, day] = ymd.split('-').map(Number)
  const dt = new Date(y, m - 1, day)
  return dt.toLocaleDateString(undefined, {
    weekday: 'short',
    month: 'short',
    day: 'numeric',
    year: 'numeric',
  })
}

function compareIsoDesc(a: string, b: string): number {
  return b.localeCompare(a)
}

function buildQuery(): Record<string, string> {
  const out: Record<string, string> = {}
  if (threatFilter.value === 'high') out.threat = 'high'
  if (competitorFilter.value !== 'all') out.competitor = competitorFilter.value
  if (categoryFilter.value !== 'all') out.category = categoryFilter.value
  if (dateScope.value === 'day') {
    out.date = 'day'
    out.day = selectedDate.value
  }
  return out
}

function applyRouteQuery() {
  syncingFromRoute = true
  const q = route.query
  threatFilter.value = qStr(q.threat) === 'high' ? 'high' : 'all'
  const comp = qStr(q.competitor)
  competitorFilter.value = comp && comp.length ? comp : 'all'
  const cat = qStr(q.category)
  categoryFilter.value =
    cat && (allCategories as readonly string[]).includes(cat) ? (cat as InsightCategory) : 'all'
  if (qStr(q.date) === 'day') {
    dateScope.value = 'day'
    const dy = qStr(q.day)
    if (dy && /^\d{4}-\d{2}-\d{2}$/.test(dy)) selectedDate.value = dy
  } else {
    dateScope.value = 'all'
  }
  void nextTick(() => {
    syncingFromRoute = false
  })
}

onMounted(() => {
  applyRouteQuery()
})

watch(
  () => route.query,
  () => {
    applyRouteQuery()
  },
  { deep: true },
)

watch([threatFilter, competitorFilter, categoryFilter, dateScope, selectedDate], () => {
  maxCards.value = 60
  if (syncingFromRoute) return
  void router.replace({ path: route.path, query: buildQuery() })
})

const competitors = computed(() => [...store.insightsByCompetitor.keys()].sort())

const filtered = computed(() => {
  const base = threatFilter.value === 'high' ? store.highThreatInsights : store.insights
  let list = base
  if (competitorFilter.value !== 'all') list = list.filter(i => i.competitorName === competitorFilter.value)
  if (categoryFilter.value !== 'all') list = list.filter(i => i.category === categoryFilter.value)
  return list
})

const filteredByDate = computed(() => {
  let list = [...filtered.value]
  if (dateScope.value === 'day') {
    list = list.filter(i => dayKeyLocal(insightSortIso(i)) === selectedDate.value)
  }
  list.sort((a, b) => compareIsoDesc(insightSortIso(a), insightSortIso(b)))
  return list
})

type DayGroup = { key: string; heading: string; items: Insight[] }

const groupedByDay = computed((): DayGroup[] => {
  const map = new Map<string, Insight[]>()
  for (const i of filteredByDate.value) {
    const k = dayKeyLocal(insightSortIso(i))
    if (!map.has(k)) map.set(k, [])
    map.get(k)!.push(i)
  }
  const keys = [...map.keys()].sort((a, b) => b.localeCompare(a))
  return keys.map(k => ({
    key: k,
    heading: formatDayHeading(k),
    items: map.get(k)!,
  }))
})

const totalFilteredCards = computed(() =>
  groupedByDay.value.reduce((s, g) => s + g.items.length, 0),
)

const displayedGroups = computed((): DayGroup[] => {
  let n = 0
  const cap = maxCards.value
  const result: DayGroup[] = []
  for (const g of groupedByDay.value) {
    if (n >= cap) break
    const avail = g.items
    const need = cap - n
    if (avail.length <= need) {
      result.push(g)
      n += avail.length
    } else {
      result.push({ ...g, items: avail.slice(0, need) })
      break
    }
  }
  return result
})

const hasMoreCards = computed(() => {
  const shown = displayedGroups.value.reduce((s, g) => s + g.items.length, 0)
  return shown < totalFilteredCards.value
})

function loadMore() {
  maxCards.value += 50
}

const showEmptyDay = computed(
  () =>
    dateScope.value === 'day' &&
    filtered.value.length > 0 &&
    filteredByDate.value.length === 0,
)

function setToday() {
  selectedDate.value = todayLocal()
}

function setYesterday() {
  const d = new Date()
  d.setDate(d.getDate() - 1)
  selectedDate.value = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

watch(dateScope, scope => {
  if (scope === 'day' && !selectedDate.value) selectedDate.value = todayLocal()
})
</script>

<template>
  <div class="flex gap-6">
    <aside class="hidden w-44 shrink-0 lg:block">
      <div class="sticky top-20 space-y-1">
        <p class="mb-2 px-2 text-xs font-semibold uppercase tracking-wider text-gray-500">Filter</p>

        <button
          class="w-full rounded-md px-3 py-2 text-left text-sm transition-colors"
          :class="
            threatFilter === 'all' && competitorFilter === 'all'
              ? 'bg-gray-800 text-white'
              : 'text-gray-400 hover:bg-gray-900 hover:text-gray-200'
          "
          @click="threatFilter = 'all'; competitorFilter = 'all'"
        >
          All insights
        </button>

        <button
          class="w-full rounded-md px-3 py-2 text-left text-sm transition-colors"
          :class="
            threatFilter === 'high' && competitorFilter === 'all'
              ? 'bg-red-900/60 text-red-300'
              : 'text-gray-400 hover:bg-gray-900 hover:text-gray-200'
          "
          @click="threatFilter = 'high'; competitorFilter = 'all'"
        >
          High threat ≥7
        </button>

        <div v-if="competitors.length" class="mt-4">
          <p class="mb-2 px-2 text-xs font-semibold uppercase tracking-wider text-gray-500">Competitors</p>
          <button
            v-for="c in competitors"
            :key="c"
            class="w-full truncate rounded-md px-3 py-2 text-left text-sm transition-colors"
            :class="
              competitorFilter === c
                ? threatFilter === 'high'
                  ? 'bg-red-900/60 text-red-300'
                  : 'bg-gray-800 text-white'
                : 'text-gray-400 hover:bg-gray-900 hover:text-gray-200'
            "
            @click="competitorFilter = c"
          >
            {{ c }}
          </button>
        </div>

        <div class="mt-4 border-t border-gray-800 pt-4">
          <p class="mb-2 px-2 text-xs font-semibold uppercase tracking-wider text-gray-500">By date</p>
          <button
            class="w-full rounded-md px-3 py-2 text-left text-sm transition-colors"
            :class="
              dateScope === 'all'
                ? 'bg-gray-800 text-white'
                : 'text-gray-400 hover:bg-gray-900 hover:text-gray-200'
            "
            @click="dateScope = 'all'"
          >
            All dates
          </button>
          <button
            class="mt-1 w-full rounded-md px-3 py-2 text-left text-sm transition-colors"
            :class="
              dateScope === 'day'
                ? 'bg-gray-800 text-white'
                : 'text-gray-400 hover:bg-gray-900 hover:text-gray-200'
            "
            @click="dateScope = 'day'"
          >
            Single day
          </button>
          <div v-if="dateScope === 'day'" class="mt-2 space-y-2 px-1">
            <label class="sr-only" for="insight-date-filter">Filter insights by calendar day</label>
            <input
              id="insight-date-filter"
              v-model="selectedDate"
              type="date"
              class="w-full rounded-md border border-gray-700 bg-gray-900 px-2 py-1.5 text-sm text-gray-200 [color-scheme:dark]"
              aria-label="Filter insights by calendar day"
            />
            <div class="flex gap-1">
              <button
                type="button"
                class="flex-1 rounded-md bg-gray-900 px-2 py-1 text-xs text-gray-300 ring-1 ring-gray-700 hover:bg-gray-800"
                @click="setToday"
              >
                Today
              </button>
              <button
                type="button"
                class="flex-1 rounded-md bg-gray-900 px-2 py-1 text-xs text-gray-300 ring-1 ring-gray-700 hover:bg-gray-800"
                @click="setYesterday"
              >
                Yesterday
              </button>
            </div>
          </div>

          <div class="mt-4">
            <p class="mb-2 px-2 text-xs font-semibold uppercase tracking-wider text-gray-500">Category</p>

            <button
              class="w-full rounded-md px-3 py-2 text-left text-sm transition-colors"
              :class="
                categoryFilter === 'all'
                  ? 'bg-gray-800 text-white'
                  : 'text-gray-400 hover:bg-gray-900 hover:text-gray-200'
              "
              @click="categoryFilter = 'all'"
            >
              All categories
            </button>

            <button
              v-for="c in allCategories"
              :key="c"
              class="mt-1 w-full truncate rounded-md px-3 py-2 text-left text-sm transition-colors"
              :class="
                categoryFilter === c
                  ? 'bg-gray-800 text-white'
                  : 'text-gray-400 hover:bg-gray-900 hover:text-gray-200'
              "
              @click="categoryFilter = c"
            >
              {{ categoryLabel(c) }}
            </button>
          </div>
        </div>
      </div>
    </aside>

    <div class="min-w-0 flex-1">
      <div class="mb-4 flex gap-2 overflow-x-auto pb-1 lg:hidden">
        <button
          v-for="f in ['all', 'high', ...competitors]"
          :key="f"
          class="shrink-0 rounded-full px-3 py-1 text-xs font-medium transition-colors"
          :class="
            f === 'all'
              ? threatFilter === 'all' && competitorFilter === 'all'
                ? 'bg-gray-700 text-white'
                : 'bg-gray-900 text-gray-400'
              : f === 'high'
                ? threatFilter === 'high' && competitorFilter === 'all'
                  ? 'bg-red-900/60 text-red-300'
                  : 'bg-gray-900 text-gray-400'
                : competitorFilter === f
                  ? threatFilter === 'high'
                    ? 'bg-red-900/60 text-red-300'
                    : 'bg-gray-700 text-white'
                  : 'bg-gray-900 text-gray-400'
          "
          @click="
            f === 'all'
              ? (threatFilter = 'all', competitorFilter = 'all')
              : f === 'high'
                ? (threatFilter = 'high', competitorFilter = 'all')
                : (competitorFilter = f)
          "
        >
          {{ f === 'all' ? 'All' : f === 'high' ? 'High Threat' : f }}
        </button>
      </div>

      <div class="mb-3 flex flex-wrap items-center gap-2 lg:hidden">
        <span class="text-xs text-gray-500">Date:</span>
        <button
          type="button"
          class="rounded-full px-3 py-1 text-xs font-medium transition-colors"
          :class="dateScope === 'all' ? 'bg-gray-700 text-white' : 'bg-gray-900 text-gray-400'"
          @click="dateScope = 'all'"
        >
          All dates
        </button>
        <button
          type="button"
          class="rounded-full px-3 py-1 text-xs font-medium transition-colors"
          :class="dateScope === 'day' ? 'bg-gray-700 text-white' : 'bg-gray-900 text-gray-400'"
          @click="dateScope = 'day'"
        >
          Day
        </button>
        <template v-if="dateScope === 'day'">
          <input
            v-model="selectedDate"
            type="date"
            class="rounded-md border border-gray-700 bg-gray-900 px-2 py-1 text-xs text-gray-200 [color-scheme:dark]"
            aria-label="Filter insights by calendar day"
          />
        </template>
      </div>

      <div class="mb-3 flex flex-wrap items-center gap-2 lg:hidden">
        <span class="text-xs text-gray-500">Category:</span>
        <button
          type="button"
          class="rounded-full px-3 py-1 text-xs font-medium transition-colors"
          :class="categoryFilter === 'all' ? 'bg-gray-700 text-white' : 'bg-gray-900 text-gray-400'"
          @click="categoryFilter = 'all'"
        >
          All
        </button>
        <button
          v-for="c in allCategories"
          :key="c"
          type="button"
          class="rounded-full px-3 py-1 text-xs font-medium transition-colors"
          :class="categoryFilter === c ? 'bg-gray-700 text-white' : 'bg-gray-900 text-gray-400'"
          @click="categoryFilter = c"
        >
          {{ categoryLabel(c) }}
        </button>
      </div>

      <div v-if="store.isBootLoading" class="space-y-3" aria-hidden="true">
        <div
          v-for="n in skeletonSlots"
          :key="n"
          class="animate-pulse rounded-lg border-l-4 border-gray-700 bg-gray-900 p-4"
        >
          <div class="flex justify-between gap-2">
            <div class="h-4 w-32 rounded bg-gray-800" />
            <div class="h-4 w-20 rounded bg-gray-800" />
          </div>
          <div class="mt-3 h-4 w-full rounded bg-gray-800" />
          <div class="mt-2 h-3 w-4/5 rounded bg-gray-800/80" />
          <div class="mt-3 h-12 w-full rounded bg-gray-800/60" />
        </div>
      </div>

      <div
        v-else-if="!filtered.length"
        class="flex h-64 flex-col items-center justify-center gap-3 rounded-lg border border-dashed border-gray-800 text-center"
      >
        <div class="text-4xl">📡</div>
        <p class="text-sm text-gray-500">{{ emptyFeedMessage }}</p>
      </div>

      <div
        v-else-if="showEmptyDay"
        class="flex h-48 flex-col items-center justify-center gap-2 rounded-lg border border-dashed border-gray-800 text-center"
      >
        <p class="text-sm text-gray-400">No insights for {{ selectedDate }}</p>
        <p class="text-xs text-gray-500">Try another day or choose “All dates”.</p>
      </div>

      <div v-else class="space-y-8">
        <section
          v-for="group in displayedGroups"
          :key="group.key"
          class="space-y-3"
          :aria-labelledby="`feed-day-${group.key}`"
        >
          <h3
            :id="`feed-day-${group.key}`"
            class="sticky top-0 z-10 -mx-1 mb-3 border-b border-gray-800 bg-gray-950/95 px-1 py-2 text-xs font-semibold uppercase tracking-wider text-gray-400 backdrop-blur-sm"
          >
            {{ group.heading }}
          </h3>
          <TransitionGroup name="feed" tag="div">
            <ThreatCard
              v-for="insight in group.items"
              :key="insight.id"
              :insight="insight"
              v-memo="[insight.id, insight.isNew, insight.threatLevel]"
            />
          </TransitionGroup>
        </section>

        <div v-if="hasMoreCards" class="flex justify-center pt-2">
          <button
            type="button"
            class="rounded-md bg-gray-800 px-4 py-2 text-sm text-gray-200 ring-1 ring-gray-700 hover:bg-gray-700"
            @click="loadMore"
          >
            Load more
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.feed-enter-active {
  transition: all 0.35s ease;
}
.feed-enter-from {
  opacity: 0;
  transform: translateY(-12px);
}
.feed-enter-to {
  opacity: 1;
  transform: translateY(0);
}
</style>
