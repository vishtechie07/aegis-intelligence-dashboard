import { describe, it, expect, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import { useInsightStore } from '@/stores/insightStore'
import { useSettingsStore } from '@/stores/settingsStore'
import InsightFeed from './InsightFeed.vue'
import type { Insight } from '@/types/insight'

function mountInsightFeed() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/', name: 'home', component: { template: '<div/>' } }],
  })
  return mount(InsightFeed, { global: { plugins: [router] } })
}

function makeInsight(id: number, threatLevel = 5, competitorName = 'Acme'): Insight {
  return {
    id, newsId: id * 10, competitorName, title: `Article ${id}`,
    sourceUrl: `https://example.com/${id}`, sourceType: 'GDELT',
    agentName: 'Strategist', category: 'FINANCIAL_MOVE',
    threatLevel, summary: `Summary ${id}`, strategicAdvice: `Advice ${id}`,
    publishedAt: new Date().toISOString(), processedAt: new Date().toISOString(),
    isNew: false,
  }
}

describe('InsightFeed', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    useInsightStore().setBootStatus('ready')
  })

  it('shows empty state when no insights', () => {
    const settings = useSettingsStore()
    settings.isConfigured = true
    const store = useInsightStore()
    store.setBootStatus('ready')
    const wrapper = mountInsightFeed()
    expect(wrapper.text()).toContain('Reconnecting to live feed')
  })

  it('shows listening text when connected and empty', async () => {
    const settings = useSettingsStore()
    settings.isConfigured = true
    const store = useInsightStore()
    store.setBootStatus('ready')
    store.setStatus('connected')

    const wrapper = mountInsightFeed()
    expect(wrapper.text()).toContain('Live — no insights yet')
  })

  it('shows skeleton placeholders while boot loading', () => {
    const store = useInsightStore()
    store.setBootStatus('loading')
    store.loadInitial([])
    const wrapper = mountInsightFeed()
    expect(wrapper.find('.animate-pulse').exists()).toBe(true)
  })

  it('renders a ThreatCard for each insight', () => {
    const store = useInsightStore()
    store.loadInitial([makeInsight(1), makeInsight(2), makeInsight(3)])

    const wrapper = mountInsightFeed()
    const cards = wrapper.findAll('[class*="border-l-4"]')
    expect(cards).toHaveLength(3)
  })

  it('filters to high threat insights when filter selected', async () => {
    const store = useInsightStore()
    store.loadInitial([
      makeInsight(1, 5),
      makeInsight(2, 9),
      makeInsight(3, 8),
    ])

    const wrapper = mountInsightFeed()
    const highThreatBtn = wrapper.findAll('button').find(b => b.text().includes('High threat'))!
    await highThreatBtn.trigger('click')

    const cards = wrapper.findAll('[class*="border-l-4"]')
    expect(cards).toHaveLength(2)
  })

  it('filters to specific competitor when competitor filter selected', async () => {
    const store = useInsightStore()
    store.loadInitial([
      makeInsight(1, 5, 'OpenAI'),
      makeInsight(2, 5, 'Google'),
      makeInsight(3, 5, 'OpenAI'),
    ])

    const wrapper = mountInsightFeed()
    const openAiBtn = wrapper.findAll('button').find(b => b.text() === 'OpenAI')!
    await openAiBtn.trigger('click')

    const cards = wrapper.findAll('[class*="border-l-4"]')
    expect(cards).toHaveLength(2)
  })

  it('shows all insights when All filter selected after competitor filter', async () => {
    const store = useInsightStore()
    store.loadInitial([makeInsight(1, 5, 'OpenAI'), makeInsight(2, 5, 'Google')])

    const wrapper = mountInsightFeed()
    const openAiBtn = wrapper.findAll('button').find(b => b.text() === 'OpenAI')!
    await openAiBtn.trigger('click')

    const allBtn = wrapper.findAll('button').find(b => b.text() === 'All insights')!
    await allBtn.trigger('click')

    const cards = wrapper.findAll('[class*="border-l-4"]')
    expect(cards).toHaveLength(2)
  })

  it('shows competitor names in sidebar filter', () => {
    const store = useInsightStore()
    store.loadInitial([makeInsight(1, 5, 'Microsoft'), makeInsight(2, 5, 'Amazon')])

    const wrapper = mountInsightFeed()
    expect(wrapper.text()).toContain('Microsoft')
    expect(wrapper.text()).toContain('Amazon')
  })

  it('groups insights under day headings (local date from publishedAt)', () => {
    useSettingsStore().isRuntimeKey = true
    const store = useInsightStore()
    const a = makeInsight(1, 5, 'Acme')
    const b = makeInsight(2, 5, 'Acme')
    a.publishedAt = '2025-12-02T10:00:00.000Z'
    b.publishedAt = '2025-10-30T08:00:00.000Z'
    store.loadInitial([a, b])

    const wrapper = mountInsightFeed()
    const headings = wrapper.findAll('h3')
    expect(headings.length).toBe(2)
  })
})
