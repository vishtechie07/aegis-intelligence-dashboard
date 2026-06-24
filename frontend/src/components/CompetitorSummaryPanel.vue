<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { CompetitorInsightSummary } from '@/types/insight'
import { fetchCompetitorSummary } from '@/composables/useInsightFeed'
import { sourceLabel } from '@/lib/insightLabels'

const props = defineProps<{ competitor: string | null }>()
const emit = defineEmits<{ close: [] }>()

const summary = ref<CompetitorInsightSummary | null>(null)
const loading = ref(false)

watch(
  () => props.competitor,
  async name => {
    if (!name) {
      summary.value = null
      return
    }
    loading.value = true
    try {
      summary.value = await fetchCompetitorSummary(name)
    } catch {
      summary.value = null
    } finally {
      loading.value = false
    }
  },
  { immediate: true },
)

const open = computed(() => !!props.competitor)
</script>

<template>
  <Teleport to="body">
    <div
      v-if="open"
      class="fixed inset-0 z-40 flex justify-end bg-black/50 backdrop-blur-sm"
      @click.self="emit('close')"
    >
      <aside
        class="h-full w-full max-w-md overflow-y-auto border-l border-gray-800 bg-gray-950 p-5 shadow-xl"
        role="dialog"
        aria-label="Competitor summary"
      >
        <div class="mb-4 flex items-start justify-between gap-2">
          <div>
            <h2 class="text-lg font-bold text-white">{{ competitor }}</h2>
            <p class="text-xs text-gray-500">Competitor intelligence summary</p>
          </div>
          <button
            type="button"
            class="rounded-md px-2 py-1 text-gray-400 hover:bg-gray-800 hover:text-white"
            @click="emit('close')"
          >
            ✕
          </button>
        </div>

        <div v-if="loading" class="text-sm text-gray-500">Loading…</div>
        <template v-else-if="summary">
          <div class="mb-4 grid grid-cols-2 gap-2">
            <div class="rounded-lg bg-gray-900 p-3 ring-1 ring-gray-800">
              <p class="text-[10px] uppercase text-gray-500">Total insights</p>
              <p class="font-mono text-xl text-white">{{ summary.totalInsights }}</p>
            </div>
            <div class="rounded-lg bg-gray-900 p-3 ring-1 ring-gray-800">
              <p class="text-[10px] uppercase text-gray-500">High threat</p>
              <p class="font-mono text-xl text-red-400">{{ summary.highThreatCount }}</p>
            </div>
          </div>

          <section v-if="summary.byCategory.length" class="mb-4">
            <h3 class="mb-2 text-xs font-semibold uppercase text-gray-500">By category</h3>
            <ul class="space-y-1 text-sm text-gray-300">
              <li v-for="c in summary.byCategory" :key="c.category" class="flex justify-between">
                <span>{{ c.category.replace(/_/g, ' ') }}</span>
                <span class="font-mono text-gray-500">{{ c.count }}</span>
              </li>
            </ul>
          </section>

          <section v-if="summary.bySource.length" class="mb-4">
            <h3 class="mb-2 text-xs font-semibold uppercase text-gray-500">By source</h3>
            <ul class="space-y-1 text-sm text-gray-300">
              <li v-for="s in summary.bySource" :key="s.sourceType" class="flex justify-between">
                <span>{{ sourceLabel(s.sourceType as never) }}</span>
                <span class="font-mono text-gray-500">{{ s.count }}</span>
              </li>
            </ul>
          </section>

          <section v-if="summary.recentHighThreat.length">
            <h3 class="mb-2 text-xs font-semibold uppercase text-gray-500">Recent high threat</h3>
            <ul class="space-y-2">
              <li
                v-for="i in summary.recentHighThreat"
                :key="i.id"
                class="rounded-md bg-gray-900 p-2 text-xs ring-1 ring-gray-800"
              >
                <a
                  v-if="i.sourceUrl"
                  :href="i.sourceUrl"
                  target="_blank"
                  rel="noopener noreferrer"
                  class="font-medium text-blue-400 hover:underline"
                >
                  {{ i.title }}
                </a>
                <span v-else class="font-medium text-gray-200">{{ i.title }}</span>
                <span class="ml-2 font-mono text-red-400">{{ i.threatLevel }}/10</span>
              </li>
            </ul>
          </section>
        </template>
      </aside>
    </div>
  </Teleport>
</template>
