import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { aegisSessionHeaders } from '@/composables/useAegisSession'

export interface DemoQuotaStatus {
  trialEnabled: boolean
  usingHostedKey: boolean
  requiresUserKey: boolean
  trialMinutes: number
  secondsRemaining: number
  askAgentGraceTotal: number
  askAgentGraceRemaining: number
}

const STORAGE_KEY = 'aegis_openai_key'
const SESSION_KEY = 'aegis_openai_key_session'
const REMEMBER_FLAG = 'aegis_key_remember'
const PLACEHOLDER_KEY = 'sk-placeholder-no-real-calls'
const API_BASE = import.meta.env.VITE_API_BASE_URL ?? ''

function getApiUrl(path: string): string {
  const base = API_BASE.replace(/\/$/, '')
  return `${base}/api${path.startsWith('/') ? path : '/' + path}`
}

export const useSettingsStore = defineStore('settings', () => {
  const isConfigured = ref(false)
  const isRuntimeKey = ref(false)
  const serverKeyAvailable = ref(false)
  const isSaving = ref(false)
  const error = ref<string | null>(null)
  const successMessage = ref<string | null>(null)
  const aiKeyIssueBanner = ref<string | null>(null)
  const demoQuota = ref<DemoQuotaStatus | null>(null)

  const demoQuotaHint = computed(() => {
    const q = demoQuota.value
    if (!q?.trialEnabled || !q.usingHostedKey || q.requiresUserKey) return null
    if (q.secondsRemaining > 0) {
      const min = Math.ceil(q.secondsRemaining / 60)
      const sec = q.secondsRemaining % 60
      const time =
        min > 0 ? `${min} min${min !== 1 ? 's' : ''}${sec > 0 ? ` ${sec}s` : ''}` : `${sec}s`
      const bonus = q.askAgentGraceTotal > 0 ? `, then ${q.askAgentGraceTotal} bonus Ask Agent asks` : ''
      return `Hosted AI demo: ${time} left for Ask Agent & AI Lookup${bonus} — then add your own key.`
    }
    if (q.askAgentGraceRemaining > 0) {
      return `Demo time ended — ${q.askAgentGraceRemaining} of ${q.askAgentGraceTotal} bonus Ask Agent asks left (AI Lookup needs your key).`
    }
    return null
  })

  function dismissAiKeyIssue() {
    aiKeyIssueBanner.value = null
  }

  function reportAiKeyIssue(message: string) {
    aiKeyIssueBanner.value = message
  }

  function persistKeyLocally(apiKey: string, remember: boolean) {
    if (remember) {
      localStorage.setItem(STORAGE_KEY, apiKey)
      localStorage.setItem(REMEMBER_FLAG, '1')
      sessionStorage.removeItem(SESSION_KEY)
    } else {
      sessionStorage.setItem(SESSION_KEY, apiKey)
      localStorage.setItem(REMEMBER_FLAG, '0')
      localStorage.removeItem(STORAGE_KEY)
    }
  }

  async function checkStatus() {
    try {
      const res = await fetch(getApiUrl('/settings/status'), { headers: aegisSessionHeaders() })
      if (res.ok) {
        const data = await res.json()
        isConfigured.value = data.configured
        isRuntimeKey.value = data.runtimeKeySet
        serverKeyAvailable.value = Boolean(data.serverKeyAvailable)
        demoQuota.value = data.demoQuota ?? null
      }
    } catch { /* ignore */ }
  }

  async function saveKey(apiKey: string, remember = true): Promise<boolean> {
    error.value = null
    successMessage.value = null
    isSaving.value = true
    try {
      const res = await fetch(getApiUrl('/settings/openai-key'), {
        method: 'PUT',
        headers: aegisSessionHeaders({ 'Content-Type': 'application/json' }),
        body: JSON.stringify({ apiKey }),
      })
      const text = await res.text()
      let data: { error?: string; status?: string } = {}
      try {
        data = text ? JSON.parse(text) : {}
      } catch {
        data = {}
      }
      if (!res.ok) {
        error.value = data.error ?? `Request failed (${res.status})`
        if (res.status === 401) {
          reportAiKeyIssue('OpenAI rejected the key (401). Update it in Settings.')
        }
        return false
      }
      persistKeyLocally(apiKey, remember)
      isConfigured.value = true
      isRuntimeKey.value = true
      successMessage.value = data.status ?? 'Key saved.'
      dismissAiKeyIssue()
      return true
    } catch (e: unknown) {
      const isNetwork = e instanceof TypeError || (e instanceof Error && e.message === 'Failed to fetch')
      error.value = isNetwork
        ? 'Cannot reach backend. Run the backend (mvn spring-boot:run -Dspring-boot.run.profiles=local) and open the app via the Vite dev server (npm run dev) at http://localhost:5173 so /api is proxied to port 8080.'
        : 'Network error — is the backend running?'
      return false
    } finally {
      isSaving.value = false
    }
  }

  async function restoreFromStorage() {
    const rememberPref = localStorage.getItem(REMEMBER_FLAG)
    let stored: string | null = null
    if (rememberPref === '0') {
      stored = sessionStorage.getItem(SESSION_KEY)
    } else {
      stored = localStorage.getItem(STORAGE_KEY) ?? sessionStorage.getItem(SESSION_KEY)
    }
    if (stored && stored.trim() === PLACEHOLDER_KEY) {
      await checkStatus()
      return
    }
    if (stored) {
      await checkStatus()
      if (!isConfigured.value) await saveKey(stored, rememberPref !== '0')
    } else {
      await checkStatus()
    }
  }

  async function clearKey() {
    localStorage.removeItem(STORAGE_KEY)
    localStorage.removeItem(REMEMBER_FLAG)
    sessionStorage.removeItem(SESSION_KEY)
    error.value = null
    try {
      const res = await fetch(getApiUrl('/settings/openai-key'), {
        method: 'DELETE',
        headers: aegisSessionHeaders(),
      })
      if (res.ok) {
        const data = await res.json()
        successMessage.value = data.status ?? null
      }
    } catch {
      /* ignore */
    }
    await checkStatus()
    if (!isConfigured.value) {
      successMessage.value = null
    }
  }

  /** Whether the UI should default the “remember key” checkbox to checked */
  function prefersRememberKey(): boolean {
    return localStorage.getItem(REMEMBER_FLAG) !== '0'
  }

  return {
    isConfigured,
    isRuntimeKey,
    serverKeyAvailable,
    isSaving,
    error,
    successMessage,
    aiKeyIssueBanner,
    demoQuota,
    demoQuotaHint,
    checkStatus,
    saveKey,
    restoreFromStorage,
    clearKey,
    dismissAiKeyIssue,
    reportAiKeyIssue,
    prefersRememberKey,
  }
})
