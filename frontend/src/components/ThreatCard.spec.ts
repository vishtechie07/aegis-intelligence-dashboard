import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { setActivePinia, createPinia } from 'pinia'
import ThreatCard from './ThreatCard.vue'
import type { Insight, SourceType } from '@/types/insight'

function makeInsight(overrides: Partial<Insight> = {}): Insight {
  return {
    id: 1, newsId: 10, competitorName: 'OpenAI',
    title: 'GPT-5 has been released', sourceUrl: 'https://techcrunch.com/gpt5',
    sourceType: 'RSS', agentName: 'Strategist', category: 'PRODUCT_LAUNCH',
    threatLevel: 8, summary: 'OpenAI launched its latest model.',
    strategicAdvice: 'Accelerate your product roadmap immediately.',
    publishedAt: '2026-01-15T10:00:00Z', processedAt: '2026-01-15T10:01:00Z',
    isNew: false,
    ...overrides,
  }
}

describe('ThreatCard', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.mocked(globalThis.fetch).mockReset()
  })
  afterEach(() => vi.mocked(globalThis.fetch).mockReset())

  it('renders competitor name', () => {
    const wrapper = mount(ThreatCard, { props: { insight: makeInsight() } })
    expect(wrapper.text()).toContain('OpenAI')
  })

  it('renders article title as link', () => {
    const wrapper = mount(ThreatCard, { props: { insight: makeInsight() } })
    const link = wrapper.find('a')
    expect(link.text()).toContain('GPT-5 has been released')
    expect(link.attributes('href')).toBe('https://techcrunch.com/gpt5')
  })

  it('renders summary text', () => {
    const wrapper = mount(ThreatCard, { props: { insight: makeInsight() } })
    expect(wrapper.text()).toContain('OpenAI launched its latest model.')
  })

  it('renders strategic advice', () => {
    const wrapper = mount(ThreatCard, { props: { insight: makeInsight() } })
    expect(wrapper.text()).toContain('Accelerate your product roadmap immediately.')
  })

  function accentBorder(wrapper: ReturnType<typeof mount>) {
    return wrapper.find('.border-l-4').classes().find(c =>
      /^border-(cyan|violet|emerald|amber|pink|sky|lime|fuchsia|blue|indigo|gray)-400$/.test(c),
    )
  }

  it('uses deterministic border color for same competitor', () => {
    const a = mount(ThreatCard, { props: { insight: makeInsight({ competitorName: 'OpenAI', threatLevel: 9 }) } })
    const b = mount(ThreatCard, { props: { insight: makeInsight({ competitorName: 'OpenAI', threatLevel: 3 }) } })
    const aClass = accentBorder(a)
    const bClass = accentBorder(b)
    expect(aClass).toBeTruthy()
    expect(aClass).toBe(bClass)
  })

  it('uses different border colors for different competitors', () => {
    const a = mount(ThreatCard, { props: { insight: makeInsight({ competitorName: 'Google' }) } })
    const b = mount(ThreatCard, { props: { insight: makeInsight({ competitorName: 'Anthropic' }) } })
    const aClass = accentBorder(a)
    const bClass = accentBorder(b)
    expect(aClass).toBeTruthy()
    expect(bClass).toBeTruthy()
    expect(aClass).not.toBe(bClass)
  })

  it('shows animate-pulse ring when isNew is true', () => {
    const wrapper = mount(ThreatCard, { props: { insight: makeInsight({ isNew: true }) } })
    expect(wrapper.find('div').classes()).toContain('animate-pulse')
  })

  it('does not show animate-pulse when isNew is false', () => {
    const wrapper = mount(ThreatCard, { props: { insight: makeInsight({ isNew: false }) } })
    expect(wrapper.find('div').classes()).not.toContain('animate-pulse')
  })

  it('shows RSS source icon for RSS source type', () => {
    const wrapper = mount(ThreatCard, { props: { insight: makeInsight({ sourceType: 'RSS' }) } })
    expect(wrapper.text()).toContain('📰')
  })

  it('shows correct icons for all source types', () => {
    const iconMap: Record<SourceType, string> = {
      RSS: '📰', GDELT: '🌍', REDDIT: '🤖',
      HACKERNEWS: '🔶', EDGAR: '📋', GITHUB: '🐙',
      GOOGLENEWS: '🔍', FINANCE: '📈', CONTRACT: '🏛️', MACRO: '🏦',
    }
    for (const [sourceType, icon] of Object.entries(iconMap)) {
      const wrapper = mount(ThreatCard, { props: { insight: makeInsight({ sourceType: sourceType as SourceType }) } })
      expect(wrapper.text()).toContain(icon)
    }
  })

  it('shows category badge', () => {
    const wrapper = mount(ThreatCard, { props: { insight: makeInsight({ category: 'HIRING' }) } })
    expect(wrapper.text()).toContain('Hiring')
  })

  it('shows threat level badge', () => {
    const wrapper = mount(ThreatCard, { props: { insight: makeInsight({ threatLevel: 8 }) } })
    expect(wrapper.text()).toContain('Threat 8/10')
  })

  it('shows Ask Agent button', () => {
    const wrapper = mount(ThreatCard, { props: { insight: makeInsight() } })
    expect(wrapper.text()).toContain('Ask Agent')
  })

  it('toggles deep-dive panel on Ask Agent click', async () => {
    vi.mocked(globalThis.fetch).mockResolvedValueOnce({
      ok: true,
      json: async () => [],
    } as Response)

    const wrapper = mount(ThreatCard, { props: { insight: makeInsight() } })

    expect(wrapper.find('input[type="text"]').exists() || wrapper.find('input[type="password"]').exists())
      .toBe(false)

    await wrapper.find('button').trigger('click')

    const input = wrapper.find('input')
    expect(input.exists()).toBe(true)
    expect(wrapper.text()).toContain('Close')
  })

  it('loads full answer when a previous ask is clicked', async () => {
    const history = [{
      id: 7,
      newsId: 10,
      question: 'any issues?',
      analysis: 'Answer:\nOpenAI won the trade secrets case.\n\nStrategic implications:\n• Monitor legal risk',
      createdAt: '2026-06-17T10:00:00Z',
      sources: [{
        newsId: 10,
        title: 'GPT-5 has been released',
        excerpt: 'OpenAI launched its latest model.',
        sourceUrl: 'https://techcrunch.com/gpt5',
        currentArticle: true,
      }],
      ragUsed: true,
    }]

    vi.mocked(globalThis.fetch).mockResolvedValueOnce({
      ok: true,
      json: async () => history,
    } as Response)

    const wrapper = mount(ThreatCard, { props: { insight: makeInsight() } })
    await wrapper.find('button').trigger('click')

    await vi.waitFor(() => {
      expect(wrapper.text()).toContain('OpenAI won the trade secrets case')
      expect(wrapper.text()).toContain('Sources used (1)')
      expect(wrapper.text()).toContain('Viewing')
    })

    expect((wrapper.find('input').element as HTMLInputElement).value).toBe('any issues?')
  })

  it('calls deep-dive API on Ask button click', async () => {
    vi.mocked(globalThis.fetch)
      .mockResolvedValueOnce({ ok: true, json: async () => [] } as Response)
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({
          analysis: 'Deep analysis result',
          sources: [{
            newsId: 10,
            title: 'GPT-5 has been released',
            excerpt: 'OpenAI launched its latest model.',
            sourceUrl: 'https://techcrunch.com/gpt5',
            currentArticle: true,
          }],
          ragUsed: false,
        }),
      } as Response)
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({
          configured: true,
          runtimeKeySet: false,
          serverKeyAvailable: true,
          demoQuota: {
            trialEnabled: true,
            usingHostedKey: true,
            requiresUserKey: false,
            trialMinutes: 5,
            secondsRemaining: 120,
            askAgentGraceTotal: 5,
            askAgentGraceRemaining: 5,
          },
        }),
      } as Response)
      .mockResolvedValueOnce({ ok: true, json: async () => [] } as Response)

    const wrapper = mount(ThreatCard, { props: { insight: makeInsight() } })

    const buttons = wrapper.findAll('button')
    await buttons[0].trigger('click')

    // fill question
    await wrapper.find('input').setValue('What does this mean?')

    const askButton = wrapper.findAll('button').find(b => b.text() === 'Ask')!
    await askButton.trigger('click')

    await vi.waitFor(() => {
      expect(wrapper.text()).toContain('Deep analysis result')
    })
  })
})
