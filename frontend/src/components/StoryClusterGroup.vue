<script setup lang="ts">
import { ref } from 'vue'
import type { Insight } from '@/types/insight'
import ThreatCard from './ThreatCard.vue'

defineProps<{
  clusterKey: string
  items: Insight[]
}>()

const open = ref(true)
</script>

<template>
  <div class="rounded-lg ring-1 ring-gray-800 bg-gray-900/40 overflow-hidden">
    <button
      type="button"
      class="flex w-full items-center justify-between gap-2 px-3 py-2 text-left text-xs font-medium text-gray-300 hover:bg-gray-800/60"
      @click="open = !open"
    >
      <span>
        <span class="text-violet-400">{{ items.length }} similar headlines</span>
        <span class="ml-2 text-gray-500">{{ items[0]?.competitorName }}</span>
      </span>
      <span class="text-gray-500">{{ open ? '▾' : '▸' }}</span>
    </button>
    <div v-show="open" class="space-y-0 border-t border-gray-800 px-1 pb-1">
      <ThreatCard
        v-for="insight in items"
        :key="insight.id"
        :insight="insight"
        class="cluster-child"
      />
    </div>
  </div>
</template>

<style scoped>
.cluster-child :deep(.mb-3) {
  margin-bottom: 0.5rem;
}
</style>
