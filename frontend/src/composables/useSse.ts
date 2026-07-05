import { onMounted, onUnmounted } from 'vue'
import { useInsightStore } from '@/stores/insightStore'
import { useSettingsStore } from '@/stores/settingsStore'
import type { Insight } from '@/types/insight'

const API_BASE = import.meta.env.VITE_API_BASE_URL ?? ''
const WAKING_API_DELAY_MS = 5_000
const HEALTH_RETRY_MS = 2_000
const HEALTH_MAX_MS = 180_000
const HEALTH_REQUEST_TIMEOUT_MS = 10_000
const BOOT_REQUEST_TIMEOUT_MS = 45_000
const FEED_PAGE = 50
const META_POLL_MS = 300_000
const BOOT_LOAD_RETRIES = 5
const BOOT_LOAD_RETRY_DELAYS_MS = [0, 3_000, 5_000, 8_000, 12_000]

function apiUrl(path: string): string {
  const base = API_BASE.replace(/\/$/, '')
  return `${base}${path.startsWith('/') ? path : `/${path}`}`
}

function sleep(ms: number): Promise<void> {
  return new Promise(resolve => setTimeout(resolve, ms))
}

function fetchWithTimeout(url: string, init?: RequestInit, timeoutMs = BOOT_REQUEST_TIMEOUT_MS): Promise<Response> {
  const controller = new AbortController()
  const timer = setTimeout(() => controller.abort(), timeoutMs)
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
    return apiUrl('/actuator/health/readiness')
  }

  async function waitForApi(): Promise<boolean> {
    const deadline = Date.now() + HEALTH_MAX_MS
    while (Date.now() < deadline) {
      try {
        const res = await fetchWithTimeout(healthCheckUrl(), undefined, HEALTH_REQUEST_TIMEOUT_MS)
        if (res.ok) return true
      } catch {
        /* retry */
      }
      await sleep(HEALTH_RETRY_MS)
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

  async function refreshMeta(timeoutMs = BOOT_REQUEST_TIMEOUT_MS) {
    if (document.hidden) return
    try {
      const [statsRes, analyticsRes] = await Promise.all([
        fetchWithTimeout(apiUrl('/api/insights/stats'), undefined, timeoutMs),
        fetchWithTimeout(`${apiUrl('/api/insights/analytics')}?days=7`, undefined, timeoutMs),
      ])
      if (!statsRes.ok || !analyticsRes.ok) return
      store.setStats(await statsRes.json())
      store.setAnalytics(await analyticsRes.json())
    } catch {
      /* non-fatal */
    }
  }

  async function loadInitialOnce(): Promise<'ok' | '401' | 'fail'> {
    try {
      const params = new URLSearchParams({
        offset: '0',
        limit: String(FEED_PAGE),
        sort: 'processed',
      })
      const res = await fetchWithTimeout(
        `${apiUrl('/api/insights/feed')}?${params}`,
        undefined,
        BOOT_REQUEST_TIMEOUT_MS,
      )
      if (res.status === 401) return '401'
      if (!res.ok) return 'fail'
      const page = await res.json()
      store.setFeedPage(page.items, page.total, page.hasMore, false)
      await refreshMeta()
      return 'ok'
    } catch {
      return 'fail'
    }
  }

  async function loadInitialWithRetry(): Promise<boolean> {
    const settings = useSettingsStore()
    for (let attempt = 0; attempt < BOOT_LOAD_RETRIES; attempt++) {
      store.setBootLoadAttempt(attempt + 1)
      if (BOOT_LOAD_RETRY_DELAYS_MS[attempt] > 0) {
        await sleep(BOOT_LOAD_RETRY_DELAYS_MS[attempt])
      }
      const result = await loadInitialOnce()
      if (result === 'ok') {
        store.setBootLoadAttempt(0)
        return true
      }
      if (result === '401') {
        settings.reportAiKeyIssue(
          'Insights API returned unauthorized. Add or refresh your OpenAI key in Settings.',
        )
        store.setFeedPage([], 0, false, false)
        store.setBootLoadAttempt(0)
        return true
      }
    }
    store.setBootLoadAttempt(0)
    return false
  }

  function startMetaPoll() {
    if (pollInterval) return
    pollInterval = setInterval(() => {
      void refreshMeta()
    }, META_POLL_MS)
  }

  async function bootstrap() {
    clearWakingHint()
    store.setBootLoadAttempt(0)
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

    const loaded = await loadInitialWithRetry()
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
