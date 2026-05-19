import { defineStore } from 'pinia'
import { ref } from 'vue'

const API_BASE = import.meta.env.VITE_API_BASE_URL ?? ''

function getApiUrl(path: string): string {
  const base = API_BASE.replace(/\/$/, '')
  return `${base}/api${path.startsWith('/') ? path : '/' + path}`
}

function errorMessage(e: unknown): string {
  return e instanceof Error ? e.message : String(e)
}

export interface Competitor {
  name: string
  githubOrg: string
  description?: string | null
  ticker?: string | null
  industry?: string | null
  country?: string | null
}

export const useCompetitorStore = defineStore('competitors', () => {
  const list = ref<Competitor[]>([])
  const loading = ref(false)
  const lookupResult = ref<Competitor | null>(null)
  const lookupLoading = ref(false)
  const error = ref<string | null>(null)

  async function fetchAll() {
    loading.value = true
    error.value = null
    try {
      const r = await fetch(getApiUrl('/competitors'))
      if (!r.ok) throw new Error(`HTTP ${r.status}`)
      list.value = await r.json()
    } catch (e: unknown) {
      error.value = errorMessage(e) || 'Failed to load competitors'
    } finally {
      loading.value = false
    }
  }

  async function add(competitor: Competitor): Promise<boolean> {
    error.value = null
    try {
      const r = await fetch(getApiUrl('/competitors'), {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(competitor),
      })
      if (!r.ok) throw new Error(`HTTP ${r.status}`)
      await fetchAll()
      return true
    } catch (e: any) {
      error.value = e.message ?? 'Failed to add competitor'
      return false
    }
  }

  async function remove(name: string): Promise<boolean> {
    error.value = null
    try {
      const r = await fetch(getApiUrl(`/competitors/${encodeURIComponent(name)}`), { method: 'DELETE' })
      if (!r.ok && r.status !== 404) throw new Error(`HTTP ${r.status}`)
      list.value = list.value.filter(c => c.name !== name)
      return true
    } catch (e: unknown) {
      error.value = errorMessage(e) || 'Failed to remove competitor'
      return false
    }
  }

  async function lookup(name: string, country?: string): Promise<Competitor | null> {
    lookupLoading.value = true
    lookupResult.value = null
    error.value = null
    try {
      const params = new URLSearchParams({ name })
      if (country) params.set('country', country)
      const r = await fetch(`${getApiUrl('/competitors/lookup')}?${params}`)
      if (!r.ok) throw new Error(`HTTP ${r.status}`)
      lookupResult.value = await r.json()
      return lookupResult.value
    } catch (e: unknown) {
      error.value = errorMessage(e) || 'Lookup failed'
      return null
    } finally {
      lookupLoading.value = false
    }
  }

  return { list, loading, lookupResult, lookupLoading, error, fetchAll, add, remove, lookup }
})
