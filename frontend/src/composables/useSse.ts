import { onMounted, onUnmounted } from 'vue'
import { useInsightStore } from '@/stores/insightStore'
import { useSettingsStore } from '@/stores/settingsStore'
import { fetchAnalytics, fetchFeed, fetchStats } from '@/composables/useInsightFeed'
import type { Insight } from '@/types/insight'

const API_BASE = import.meta.env.VITE_API_BASE_URL ?? ''
const WAKING_API_DELAY_MS = 5_000
const HEALTH_RETRY_MS = 2_000
const HEALTH_MAX_MS = 120_000
const REQUEST_TIMEOUT_MS = 15_000
const FEED_PAGE = 50
const META_POLL_MS = 300_000

function apiUrl(path: string): string {
  const base = API_BASE.replace(/\/$/, '')
  return `${base}${path.startsWith('/') ? path : `/${path}`}`
}

function fetchWithTimeout(url: string, init?: RequestInit): Promise<Response> {
  const controller = new AbortController()
  const timer = setTimeout(() => controller.abort(), REQUEST_TIMEOUT_MS)
  return fetch(url, { ...init, signal: controller.signal }).finally(() => clearTimeout(timer))
}

export function useSse() {
  const store = useInsightStore()
  let eventSource: EventSource | null = null
  let retryTimer: ReturnType<typeof setTimeout> | null = null
  let retryDelay = 2000
  let wakingTimer: ReturnType<typeof setTimeout> | null = null
  let pollInterval: ReturnType<typeof setInterval> | null = null

  function scheduleWakingHint() {
    if (wakingTimer) clearTimeout(wakingTimer)
    wakingTimer = setTimeout(() => {
      if (store.bootStatus === 'loading') store.setBootStatus('waking-api')
    }, WAKING_API_DELAY_MS)
  }

  function clearWakingHint() {
    if (wakingTimer) {
      clearTimeout(wakingTimer)
      wakingTimer = null
    }
  }

  function healthCheckUrl(): string {
    if (!API_BASE) return apiUrl('/api/settings/status')
    return apiUrl('/actuator/health/liveness')
  }

  async function waitForApi(): Promise<boolean> {
    const deadline = Date.now() + HEALTH_MAX_MS
    while (Date.now() < deadline) {
      try {
        const res = await fetchWithTimeout(healthCheckUrl())
        if (res.ok) return true
      } catch {
        /* retry */
      }
      await new Promise(r => setTimeout(r, HEALTH_RETRY_MS))
    }
    return false
  }

  function connect() {
    store.setStatus('connecting')
    eventSource = new EventSource(apiUrl('/api/insights/stream'))

    eventSource.addEventListener('insight', (e: MessageEvent) => {
      const insight: Insight = JSON.parse(e.data)
      store.addInsight(insight)
      retryDelay = 2000
    })

    eventSource.onopen = () => store.setStatus('connected')

    eventSource.onerror = () => {
      store.setStatus('disconnected')
      store.incrementError()
      eventSource?.close()
      retryTimer = setTimeout(() => {
        retryDelay = Math.min(retryDelay * 2, 30_000)
        connect()
      }, retryDelay)
    }
  }

  async function refreshMeta() {
    if (document.hidden) return
    try {
      const [stats, analytics] = await Promise.all([fetchStats(), fetchAnalytics(7)])
      store.setStats(stats)
      store.setAnalytics(analytics)
    } catch {
      /* non-fatal */
    }
  }

  async function loadInitial(): Promise<boolean> {
    const settings = useSettingsStore()
    try {
      const page = await fetchFeed({ offset: 0, limit: FEED_PAGE, sort: 'processed' })
      store.setFeedPage(page.items, page.total, page.hasMore, false)
      await refreshMeta()
      return true
    } catch (e) {
      if (e instanceof Error && e.message.includes('401')) {
        settings.reportAiKeyIssue(
          'Insights API returned unauthorized. Add or refresh your OpenAI key in Settings.',
        )
        store.setFeedPage([], 0, false, false)
        return true
      }
      return false
    }
  }

  function startMetaPoll() {
    if (pollInterval) return
    pollInterval = setInterval(() => {
      void refreshMeta()
    }, META_POLL_MS)
  }

  async function bootstrap() {
    clearWakingHint()
    store.setBootStatus('loading')
    scheduleWakingHint()

    const apiUp = await waitForApi()
    clearWakingHint()
    if (!apiUp) {
      store.setBootStatus('error')
      return
    }

    store.setBootStatus('syncing')
    connect()

    const loaded = await loadInitial()
    if (!loaded) {
      store.setBootStatus('error')
      return
    }

    store.setBootStatus('ready')
    startMetaPoll()
  }

  function retryBootstrap() {
    eventSource?.close()
    eventSource = null
    void bootstrap()
  }

  onMounted(() => {
    void bootstrap()
  })

  onUnmounted(() => {
    clearWakingHint()
    if (retryTimer) clearTimeout(retryTimer)
    if (pollInterval) clearInterval(pollInterval)
    eventSource?.close()
  })

  return { retryBootstrap }
}
