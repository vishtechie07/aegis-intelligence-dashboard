import { describe, it, expect, vi, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useInsightStore } from './insightStore'
import type { Insight } from '@/types/insight'

function makeInsight(id: number, threatLevel = 5, competitorName = 'Acme'): Insight {
  return {
    id, newsId: id * 10, competitorName, title: `Title ${id}`,
    sourceUrl: `https://example.com/${id}`, sourceType: 'RSS',
    agentName: 'Strategist', category: 'PRODUCT_LAUNCH',
    threatLevel, summary: 'Summary', strategicAdvice: 'Advice',
    publishedAt: new Date().toISOString(), processedAt: new Date().toISOString(),
    contentExcerpt: null, ragAvailable: false, clusterKey: null,
  }
}

describe('insightStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.removeItem('aegis-insight-ui-state')
  })

  it('starts empty', () => {
    const store = useInsightStore()
    expect(store.insights).toHaveLength(0)
    expect(store.connectionStatus).toBe('disconnected')
  })

  it('addInsight prepends and sets isNew flag', () => {
    const store = useInsightStore()
    store.addInsight(makeInsight(1))

    expect(store.insights[0].id).toBe(1)
    expect(store.insights[0].isNew).toBe(true)
  })

  it('addInsight clears isNew flag after 3 seconds', async () => {
    vi.useFakeTimers()
    const store = useInsightStore()
    store.addInsight(makeInsight(1))

    expect(store.insights[0].isNew).toBe(true)
    vi.advanceTimersByTime(3001)
    expect(store.insights[0].isNew).toBe(false)
    vi.useRealTimers()
  })

  it('setFeedPage replaces insights', () => {
    const store = useInsightStore()
    store.addInsight(makeInsight(1))
    store.setFeedPage([makeInsight(10), makeInsight(11)], 2, false, false)

    expect(store.insights).toHaveLength(2)
    expect(store.feedTotal).toBe(2)
  })

  it('dismiss hides from visibleInsights', () => {
    const store = useInsightStore()
    store.dismiss(1)
    store.setFeedPage([makeInsight(1), makeInsight(2)], 2, false, false)
    expect(store.visibleInsights).toHaveLength(1)
  })

  it('highThreatInsights filters threatLevel >= 7', () => {
    const store = useInsightStore()
    store.setFeedPage([
      makeInsight(1, 5),
      makeInsight(2, 7),
      makeInsight(3, 9),
    ], 3, false, false)

    expect(store.highThreatInsights).toHaveLength(2)
    expect(store.highThreatInsights.every(i => i.threatLevel >= 7)).toBe(true)
  })

  it('insightsByCompetitor groups correctly', () => {
    const store = useInsightStore()
    store.setFeedPage([
      makeInsight(1, 5, 'OpenAI'),
      makeInsight(2, 5, 'Google'),
      makeInsight(3, 5, 'OpenAI'),
    ], 3, false, false)

    const map = store.insightsByCompetitor
    expect(map.get('OpenAI')).toHaveLength(2)
    expect(map.get('Google')).toHaveLength(1)
  })

  it('setStatus updates connectionStatus', () => {
    const store = useInsightStore()
    store.setStatus('connected')
    expect(store.connectionStatus).toBe('connected')
  })

  it('incrementError increments errorCount', () => {
    const store = useInsightStore()
    store.incrementError()
    store.incrementError()
    expect(store.errorCount).toBe(2)
  })
})
