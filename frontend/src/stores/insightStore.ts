import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import type {
  Insight,
  InsightAnalytics,
  InsightStats,
  InsightUiState,
} from '@/types/insight'

const UI_STATE_KEY = 'aegis-insight-ui-state'

function loadUiState(): InsightUiState {
  try {
    const raw = localStorage.getItem(UI_STATE_KEY)
    if (!raw) return { read: [], starred: [], dismissed: [] }
    const parsed = JSON.parse(raw) as InsightUiState
    return {
      read: parsed.read ?? [],
      starred: parsed.starred ?? [],
      dismissed: parsed.dismissed ?? [],
    }
  } catch {
    return { read: [], starred: [], dismissed: [] }
  }
}

function persistUiState(state: InsightUiState) {
  localStorage.setItem(UI_STATE_KEY, JSON.stringify(state))
}

export type BootStatus = 'loading' | 'waking-api' | 'syncing' | 'ready' | 'error'

export const useInsightStore = defineStore('insights', () => {
  const insights = ref<Insight[]>([])
  const feedTotal = ref(0)
  const feedHasMore = ref(false)
  const stats = ref<InsightStats | null>(null)
  const analytics = ref<InsightAnalytics | null>(null)
  const connectionStatus = ref<'connecting' | 'connected' | 'disconnected'>('disconnected')
  const bootStatus = ref<BootStatus>('loading')
  const errorCount = ref(0)
  const uiState = ref<InsightUiState>(loadUiState())

  const visibleInsights = computed(() =>
    insights.value.filter(i => !uiState.value.dismissed.includes(i.id)),
  )

  const highThreatInsights = computed(() =>
    visibleInsights.value.filter(i => i.threatLevel >= 7),
  )

  const starredInsights = computed(() =>
    visibleInsights.value.filter(i => uiState.value.starred.includes(i.id)),
  )

  const insightsByCompetitor = computed(() => {
    const map = new Map<string, Insight[]>()
    for (const insight of visibleInsights.value) {
      const list = map.get(insight.competitorName) ?? []
      list.push(insight)
      map.set(insight.competitorName, list)
    }
    return map
  })

  function isRead(id: number) {
    return uiState.value.read.includes(id)
  }

  function isStarred(id: number) {
    return uiState.value.starred.includes(id)
  }

  function markRead(id: number) {
    if (uiState.value.read.includes(id)) return
    uiState.value = { ...uiState.value, read: [...uiState.value.read, id] }
    persistUiState(uiState.value)
  }

  function toggleStar(id: number) {
    const starred = uiState.value.starred.includes(id)
      ? uiState.value.starred.filter(x => x !== id)
      : [...uiState.value.starred, id]
    uiState.value = { ...uiState.value, starred }
    persistUiState(uiState.value)
  }

  function dismiss(id: number) {
    if (uiState.value.dismissed.includes(id)) return
    uiState.value = { ...uiState.value, dismissed: [...uiState.value.dismissed, id] }
    persistUiState(uiState.value)
  }

  function markAllRead() {
    const ids = visibleInsights.value.map(i => i.id)
    const merged = [...new Set([...uiState.value.read, ...ids])]
    uiState.value = { ...uiState.value, read: merged }
    persistUiState(uiState.value)
  }

  function starredIds(): number[] {
    return uiState.value.starred
  }

  const unreadInFeed = computed(() =>
    visibleInsights.value.filter(i => !uiState.value.read.includes(i.id)),
  )

  /** @deprecated use unreadInFeed — same data, clearer name */
  const unreadInsights = unreadInFeed

  function mergeInsight(raw: Insight) {
    const idx = insights.value.findIndex(i => i.id === raw.id)
    if (idx >= 0) {
      insights.value[idx] = { ...insights.value[idx], ...raw }
      return
    }
    insights.value.unshift(raw)
  }

  function addInsight(raw: Insight) {
    const insight: Insight = { ...raw, isNew: true }
    mergeInsight(insight)
    setTimeout(() => {
      const idx = insights.value.findIndex(i => i.id === insight.id)
      if (idx >= 0) insights.value[idx] = { ...insights.value[idx], isNew: false }
    }, 3000)
  }

  function setFeedPage(items: Insight[], total: number, hasMore: boolean, append: boolean) {
    if (append) {
      const existing = new Set(insights.value.map(i => i.id))
      for (const item of items) {
        if (!existing.has(item.id)) insights.value.push(item)
      }
    } else {
      insights.value = items.map(i => ({ ...i, isNew: false }))
    }
    feedTotal.value = total
    feedHasMore.value = hasMore
  }

  function setStats(next: InsightStats) {
    stats.value = next
  }

  function setAnalytics(next: InsightAnalytics) {
    analytics.value = next
  }

  function setStatus(status: typeof connectionStatus.value) {
    connectionStatus.value = status
  }

  function setBootStatus(status: BootStatus) {
    bootStatus.value = status
  }

  const isBootLoading = computed(
    () =>
      bootStatus.value === 'loading'
      || bootStatus.value === 'waking-api'
      || bootStatus.value === 'syncing',
  )

  const isApiReady = computed(() => bootStatus.value === 'ready')

  const bootLoadAttempt = ref(0)
  const bootCycle = ref(0)

  function setBootLoadAttempt(attempt: number) {
    bootLoadAttempt.value = attempt
  }

  function setBootCycle(cycle: number) {
    bootCycle.value = cycle
  }

  function incrementError() {
    errorCount.value++
  }

  return {
    insights,
    visibleInsights,
    feedTotal,
    feedHasMore,
    stats,
    analytics,
    connectionStatus,
    bootStatus,
    isBootLoading,
    isApiReady,
    bootLoadAttempt,
    bootCycle,
    errorCount,
    highThreatInsights,
    starredInsights,
    unreadInFeed,
    unreadInsights,
    insightsByCompetitor,
    isRead,
    isStarred,
    markRead,
    toggleStar,
    dismiss,
    markAllRead,
    starredIds,
    addInsight,
    setFeedPage,
    setStats,
    setAnalytics,
    setStatus,
    setBootStatus,
    setBootLoadAttempt,
    setBootCycle,
    incrementError,
  }
})
