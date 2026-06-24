<script setup lang="ts">
import { computed } from 'vue'
import { useInsightStore } from '@/stores/insightStore'

const store = useInsightStore()

const s = computed(() => store.stats)
</script>

<template>
  <div
    v-if="s"
    class="mb-4 grid gap-2 sm:grid-cols-3 rounded-lg border border-gray-800 bg-gray-900/40 px-3 py-2"
    role="region"
    aria-label="Intelligence pipeline"
  >
    <div class="text-center sm:text-left">
      <p class="text-[10px] font-semibold uppercase tracking-wider text-gray-500">In database</p>
      <p class="mt-0.5 font-mono text-sm text-white">
        {{ s.totalInsights.toLocaleString() }}
        <span class="text-gray-500">insights</span>
        ·
        {{ s.totalArticles.toLocaleString() }}
        <span class="text-gray-500">articles</span>
      </p>
    </div>
    <div class="text-center sm:text-left">
      <p class="text-[10px] font-semibold uppercase tracking-wider text-gray-500">Today</p>
      <p class="mt-0.5 text-xs text-gray-300">
        <span class="font-mono text-emerald-400">{{ s.todayHarvested }}</span> harvested
        ·
        <span class="font-mono text-blue-400">{{ s.todayAnalyzed }}</span> analyzed
        ·
        <span class="font-mono text-gray-500">{{ s.todayFiltered }}</span> filtered
      </p>
    </div>
    <div class="text-center sm:text-left">
      <p class="text-[10px] font-semibold uppercase tracking-wider text-gray-500">High threat (≥7)</p>
      <p class="mt-0.5 font-mono text-sm text-red-400">{{ s.highThreatCount.toLocaleString() }}</p>
    </div>
  </div>
</template>
