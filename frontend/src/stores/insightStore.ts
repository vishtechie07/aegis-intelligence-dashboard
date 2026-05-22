import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import type { Insight } from '@/types/insight'

const MAX_PER_COMPETITOR = 50

function trimToMaxPerCompetitor(list: Insight[]): Insight[] {
  const result: Insight[] = []
  const countByCompetitor = new Map<string, number>()
  const sorted = [...list].sort((a, b) =>
    new Date(b.processedAt ?? b.publishedAt ?? 0).getTime() - new Date(a.processedAt ?? a.publishedAt ?? 0).getTime()
  )
  for (const i of sorted) {
    const c = i.competitorName
    const n = (countByCompetitor.get(c) ?? 0) + 1
    if (n <= MAX_PER_COMPETITOR) {
      countByCompetitor.set(c, n)
      result.push(i)
    }
  }
  return result
}

export type BootStatus = 'loading' | 'waking-api' | 'ready' | 'error'

export const useInsightStore = defineStore('insights', () => {
  const insights = ref<Insight[]>([])
  const connectionStatus = ref<'connecting' | 'connected' | 'disconnected'>('disconnected')
  const bootStatus = ref<BootStatus>('loading')
  const errorCount = ref(0)

  const highThreatInsights = computed(() =>
    insights.value.filter(i => i.threatLevel >= 7)
  )

  const insightsByCompetitor = computed(() => {
    const map = new Map<string, Insight[]>()
    for (const insight of insights.value) {
      const list = map.get(insight.competitorName) ?? []
      list.push(insight)
      map.set(insight.competitorName, list)
    }
    return map
  })

  function addInsight(raw: Insight) {
    const insight: Insight = { ...raw, isNew: true }
    insights.value.unshift(insight)

    setTimeout(() => {
      const idx = insights.value.findIndex(i => i.id === insight.id)
      if (idx >= 0) insights.value[idx] = { ...insights.value[idx], isNew: false }
    }, 3000)

    insights.value = trimToMaxPerCompetitor(insights.value)
  }

  function loadInitial(items: Insight[]) {
    insights.value = trimToMaxPerCompetitor(items.map(i => ({ ...i, isNew: false })))
  }

  function setStatus(status: typeof connectionStatus.value) {
    connectionStatus.value = status
  }

  function setBootStatus(status: BootStatus) {
    bootStatus.value = status
  }

  const isBootLoading = computed(
    () => bootStatus.value === 'loading' || bootStatus.value === 'waking-api',
  )

  function incrementError() {
    errorCount.value++
  }

  return {
    insights,
    connectionStatus,
    bootStatus,
    isBootLoading,
    errorCount,
    highThreatInsights,
    insightsByCompetitor,
    addInsight,
    loadInitial,
    setStatus,
    setBootStatus,
    incrementError,
  }
})
