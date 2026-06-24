import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import { useInsightStore } from '@/stores/insightStore'
import { useSettingsStore } from '@/stores/settingsStore'
import InsightFeed from './InsightFeed.vue'
import type { Insight } from '@/types/insight'

function mountInsightFeed() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', name: 'home', component: { template: '<div/>' } },
      { path: '/competitor/:name', name: 'competitor', component: { template: '<div/>' } },
    ],
  })
  return mount(InsightFeed, {
    global: {
      plugins: [router],
      stubs: { RouterLink: { template: '<a><slot /></a>', props: ['to'] } },
    },
  })
}

function makeInsight(id: number, threatLevel = 5, competitorName = 'Acme'): Insight {
  return {
    id, newsId: id * 10, competitorName, title: `Article ${id}`,
    sourceUrl: `https://example.com/${id}`, sourceType: 'GDELT',
    agentName: 'Strategist', category: 'FINANCIAL_MOVE',
    threatLevel, summary: `Summary ${id}`, strategicAdvice: `Advice ${id}`,
    publishedAt: new Date().toISOString(), processedAt: new Date().toISOString(),
    contentExcerpt: null, ragAvailable: false, clusterKey: null,
    isNew: false,
  }
}

describe('InsightFeed', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    useInsightStore().setBootStatus('ready')
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({ items: [], total: 0, hasMore: false }),
    }))
  })

  it('shows empty state when no insights', async () => {
    const settings = useSettingsStore()
    settings.isConfigured = true
    useInsightStore().setBootStatus('ready')
    const wrapper = mountInsightFeed()
    await flushPromises()
    expect(wrapper.text()).toMatch(/No insights match/)
  })

  it('shows skeleton placeholders while boot loading', () => {
    const store = useInsightStore()
    store.setBootStatus('loading')
    const wrapper = mountInsightFeed()
    expect(wrapper.find('.animate-pulse').exists()).toBe(true)
  })

  it('renders a ThreatCard for each insight', () => {
    const store = useInsightStore()
    store.setFeedPage([makeInsight(1), makeInsight(2), makeInsight(3)], 3, false, false)

    const wrapper = mountInsightFeed()
    const cards = wrapper.findAll('[class*="border-l-4"]')
    expect(cards.length).toBeGreaterThanOrEqual(3)
  })

  it('selects high threat filter', async () => {
    const store = useInsightStore()
    store.setFeedPage([makeInsight(1, 5), makeInsight(2, 9)], 2, false, false)

    const wrapper = mountInsightFeed()
    const highThreatBtn = wrapper.findAll('button').find(b => b.text().includes('High threat'))!
    expect(highThreatBtn).toBeTruthy()
    await highThreatBtn.trigger('click')
    expect(highThreatBtn.classes().join(' ')).toMatch(/red/)
  })

  it('groups insights under day headings', () => {
    const store = useInsightStore()
    const a = makeInsight(1, 5, 'Acme')
    const b = makeInsight(2, 5, 'Acme')
    a.publishedAt = '2025-12-02T10:00:00.000Z'
    b.publishedAt = '2025-10-30T08:00:00.000Z'
    store.setFeedPage([a, b], 2, false, false)

    const wrapper = mountInsightFeed()
    const headings = wrapper.findAll('h3')
    expect(headings.length).toBe(2)
  })
})
