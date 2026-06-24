<script setup lang="ts">
import { computed } from 'vue'
import { useInsightStore } from '@/stores/insightStore'
import { sourceLabel } from '@/lib/insightLabels'

const store = useInsightStore()

const sources = computed(() => store.analytics?.sourcesLast7Days ?? [])
const heatmap = computed(() => store.analytics?.highThreatByCompetitor ?? [])
const maxHeat = computed(() => Math.max(1, ...heatmap.value.map(h => h.count)))
</script>

<template>
  <div
    v-if="sources.length || heatmap.length"
    class="mb-4 grid gap-3 lg:grid-cols-2"
  >
    <div
      v-if="sources.length"
      class="rounded-lg border border-gray-800 bg-gray-900/30 px-3 py-2"
      aria-label="Source mix last 7 days"
    >
      <p class="mb-2 text-[10px] font-semibold uppercase tracking-wider text-gray-500">
        Source mix · 7d
      </p>
      <ul class="space-y-1.5">
        <li
          v-for="s in sources"
          :key="s.sourceType"
          class="flex items-center gap-2 text-xs"
        >
          <span class="w-24 shrink-0 truncate text-gray-400">{{ sourceLabel(s.sourceType as never) }}</span>
          <div class="h-2 flex-1 overflow-hidden rounded-full bg-gray-800">
            <div
              class="h-full rounded-full bg-cyan-600/80"
              :style="{ width: `${(s.count / Math.max(1, sources[0]?.count ?? 1)) * 100}%` }"
            />
          </div>
          <span class="w-8 text-right font-mono text-gray-500">{{ s.count }}</span>
        </li>
      </ul>
    </div>

    <div
      v-if="heatmap.length"
      class="rounded-lg border border-gray-800 bg-gray-900/30 px-3 py-2"
      aria-label="High threat by competitor"
    >
      <p class="mb-2 text-[10px] font-semibold uppercase tracking-wider text-gray-500">
        High threat by competitor · 7d
      </p>
      <ul class="space-y-1.5">
        <li
          v-for="h in heatmap"
          :key="h.competitorName"
          class="flex items-center gap-2 text-xs"
        >
          <span class="w-24 shrink-0 truncate text-gray-300">{{ h.competitorName }}</span>
          <div class="h-2 flex-1 overflow-hidden rounded-full bg-gray-800">
            <div
              class="h-full rounded-full bg-red-600/70"
              :style="{ width: `${(h.count / maxHeat) * 100}%` }"
            />
          </div>
          <span class="w-8 text-right font-mono text-red-400">{{ h.count }}</span>
        </li>
      </ul>
    </div>
  </div>
</template>
