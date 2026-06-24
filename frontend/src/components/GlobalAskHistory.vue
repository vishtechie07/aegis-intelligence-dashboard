<script setup lang="ts">
import { onMounted, ref } from 'vue'
import type { DeepDiveHistoryEntry } from '@/types/insight'

const API_BASE = import.meta.env.VITE_API_BASE_URL ?? ''
const open = ref(false)
const entries = ref<DeepDiveHistoryEntry[]>([])
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    const res = await fetch(`${API_BASE}/api/insights/deep-dive/history/recent`)
    entries.value = res.ok ? await res.json() : []
  } catch {
    entries.value = []
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="mb-4 rounded-lg border border-gray-800 bg-gray-900/30">
    <button
      type="button"
      class="flex w-full items-center justify-between px-3 py-2 text-left text-xs font-semibold uppercase tracking-wider text-gray-500 hover:text-gray-300"
      @click="open = !open"
    >
      <span>Recent Ask Agent · all insights</span>
      <span>{{ open ? '▾' : '▸' }}</span>
    </button>
    <div v-if="open" class="border-t border-gray-800 px-3 py-2">
      <p v-if="loading" class="text-xs text-gray-500">Loading…</p>
      <p v-else-if="!entries.length" class="text-xs text-gray-500">No asks yet.</p>
      <ul v-else class="max-h-40 space-y-2 overflow-y-auto">
        <li
          v-for="e in entries"
          :key="e.id"
          class="rounded-md bg-gray-950/80 p-2 text-xs ring-1 ring-gray-800"
        >
          <p class="font-medium text-gray-300">{{ e.question }}</p>
          <p class="mt-0.5 line-clamp-2 text-gray-500">{{ e.analysis }}</p>
          <p class="mt-1 text-[10px] text-gray-600">
            news #{{ e.newsId }}
            <span v-if="e.ragUsed"> · RAG</span>
          </p>
        </li>
      </ul>
    </div>
  </div>
</template>
