import { onMounted, onUnmounted } from 'vue'
import { useInsightStore } from '@/stores/insightStore'
import { useSettingsStore } from '@/stores/settingsStore'
import type { Insight } from '@/types/insight'

const API_BASE = import.meta.env.VITE_API_BASE_URL ?? ''
const WAKING_API_DELAY_MS = 5_000
const HEALTH_RETRY_MS = 2_000
const HEALTH_MAX_MS = 90_000
const REQUEST_TIMEOUT_MS = 15_000

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
    return API_BASE ? apiUrl('/actuator/health') : apiUrl('/api/settings/status')
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

  async function loadInitial(): Promise<boolean> {
    const settings = useSettingsStore()
    try {
      const res = await fetchWithTimeout(apiUrl('/api/insights/latest?limit=50'))
      if (res.status === 401 || res.status === 403) {
        settings.reportAiKeyIssue(
          'Insights API returned unauthorized. Add or refresh your OpenAI key in Settings.',
        )
        store.loadInitial([])
        return true
      }
      if (res.status === 429) {
        settings.reportAiKeyIssue('Rate limited while loading insights. Try again shortly.')
        store.loadInitial([])
        return true
      }
      if (res.ok) {
        const data: Insight[] = await res.json()
        store.loadInitial(data)
        return true
      }
      return false
    } catch {
      return false
    }
  }

  async function bootstrap() {
    clearWakingHint()
    store.setBootStatus('loading')
    scheduleWakingHint()

    const apiUp = await waitForApi()
    if (!apiUp) {
      clearWakingHint()
      store.setBootStatus('error')
      return
    }

    const loaded = await loadInitial()
    clearWakingHint()
    if (!loaded) {
      store.setBootStatus('error')
      return
    }

    store.setBootStatus('ready')
    connect()
    if (!pollInterval) {
      pollInterval = setInterval(loadInitial, 15_000)
    }
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
