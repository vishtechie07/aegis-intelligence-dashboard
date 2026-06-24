<script setup lang="ts">
import { useInsightStore } from '@/stores/insightStore'
import type { DayGroup } from '@/composables/useFeedGrouping'
import type { GroupBy, Insight } from '@/types/insight'
import StoryClusterGroup from './StoryClusterGroup.vue'
import ThreatCard from './ThreatCard.vue'

defineProps<{
  groupedByDay: DayGroup[]
  groupBy: GroupBy
  filtered: Insight[]
  feedLoading: boolean
  feedError: boolean
  emptyMessage: string
}>()

const emit = defineEmits<{ retry: []; 'load-more': [] }>()

const store = useInsightStore()
const skeletonSlots = [1, 2, 3, 4]
</script>

<template>
  <div v-if="store.isBootLoading || feedLoading" class="space-y-3" aria-hidden="true">
    <div v-for="n in skeletonSlots" :key="n" class="animate-pulse rounded-lg border-l-4 border-gray-700 bg-gray-900 p-4">
      <div class="h-4 w-32 rounded bg-gray-800" />
      <div class="mt-3 h-4 w-full rounded bg-gray-800" />
    </div>
  </div>

  <div
    v-else-if="!filtered.length"
    class="flex h-64 flex-col items-center justify-center gap-3 rounded-lg border border-dashed border-gray-800 text-center"
  >
    <div class="text-4xl">📡</div>
    <p class="text-sm text-gray-500">{{ emptyMessage }}</p>
    <button type="button" class="text-xs text-blue-400 underline" @click="emit('retry')">Retry</button>
  </div>

  <div v-else class="space-y-8">
    <section v-for="group in groupedByDay" :key="group.key" class="space-y-3">
      <h3 class="sticky top-16 z-10 border-b border-gray-800 bg-gray-950/95 py-2 text-xs font-semibold uppercase text-gray-400 backdrop-blur-sm">
        {{ group.heading }}
        <span class="ml-2 font-normal text-gray-600">({{ groupBy === 'processed' ? 'processed' : 'published' }})</span>
      </h3>
      <TransitionGroup name="feed" tag="div" class="space-y-3">
        <template v-for="unit in group.units" :key="unit.type === 'cluster' ? unit.key : unit.insight.id">
          <StoryClusterGroup
            v-if="unit.type === 'cluster'"
            :cluster-key="unit.key"
            :items="unit.items"
          />
          <ThreatCard
            v-else
            :insight="unit.insight"
            v-memo="[unit.insight.id, unit.insight.isNew, unit.insight.threatLevel, store.isStarred(unit.insight.id), store.isRead(unit.insight.id)]"
          />
        </template>
      </TransitionGroup>
    </section>

    <div v-if="store.feedHasMore" class="flex justify-center pt-2">
      <button
        type="button"
        class="rounded-md bg-gray-800 px-4 py-2 text-sm text-gray-200 ring-1 ring-gray-700 hover:bg-gray-700 disabled:opacity-50"
        :disabled="feedLoading"
        @click="emit('load-more')"
      >
        {{ feedLoading ? 'Loading…' : 'Load more' }}
      </button>
    </div>
  </div>
</template>

<style scoped>
.feed-enter-active { transition: all 0.35s ease; }
.feed-enter-from { opacity: 0; transform: translateY(-12px); }
.feed-enter-to { opacity: 1; transform: translateY(0); }
</style>
