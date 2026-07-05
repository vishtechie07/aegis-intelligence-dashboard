<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'

const API_BASE = import.meta.env.VITE_API_BASE_URL ?? ''

const status = ref<Record<string, string>>({})
const loadError = ref(false)

const POLL_MS = 300_000
let timer: ReturnType<typeof setInterval> | null = null

function formatShort(iso: string): string {
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return iso
  return d.toLocaleString(undefined, { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' })
}

const entries = computed(() =>
  Object.entries(status.value)
    .map(([source, iso]) => ({ source, iso, label: formatShort(iso) }))
    .sort((a, b) => a.source.localeCompare(b.source)),
)

async function fetchStatus() {
  if (document.hidden) return
  try {
    const res = await fetch(`${API_BASE}/api/harvest/status`)
    if (!res.ok) throw new Error(String(res.status))
    status.value = await res.json()
    loadError.value = false
  } catch {
    loadError.value = true
  }
}

function startPoll() {
  if (timer) return
  timer = setInterval(() => void fetchStatus(), POLL_MS)
}

function stopPoll() {
  if (!timer) return
  clearInterval(timer)
  timer = null
}

function onVisibilityChange() {
  if (document.hidden) {
    stopPoll()
    return
  }
  void fetchStatus()
  startPoll()
}

onMounted(() => {
  void fetchStatus()
  startPoll()
  document.addEventListener('visibilitychange', onVisibilityChange)
})

onUnmounted(() => {
  stopPoll()
  document.removeEventListener('visibilitychange', onVisibilityChange)
})
</script>

<template>
  <div
    class="mb-4 rounded-lg border border-gray-800 bg-gray-900/40 px-3 py-2"
    role="region"
    aria-label="Harvester activity"
  >
    <div class="mb-1.5 flex flex-wrap items-center justify-between gap-2">
      <span class="text-xs font-semibold uppercase tracking-wider text-gray-500">Harvest sources</span>
      <span v-if="loadError" class="text-xs text-amber-400">Status unavailable</span>
    </div>
    <div v-if="entries.length" class="flex flex-wrap gap-2">
      <span
        v-for="e in entries"
        :key="e.source"
        class="inline-flex items-center gap-1 rounded-md bg-gray-950/80 px-2 py-1 text-[11px] text-gray-300 ring-1 ring-gray-800"
        :title="e.iso"
      >
        <span class="font-mono uppercase text-gray-500">{{ e.source }}</span>
        <span class="text-gray-400">{{ e.label }}</span>
      </span>
    </div>
    <p v-else class="text-xs text-gray-500">No articles ingested yet from tracked sources.</p>
  </div>
</template>
