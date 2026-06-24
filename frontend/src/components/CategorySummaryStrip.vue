<script setup lang="ts">
import { computed } from 'vue'
import { useInsightStore } from '@/stores/insightStore'

const store = useInsightStore()

const categories = computed(() => store.analytics?.categoriesLast7Days ?? [])
const max = computed(() => Math.max(1, ...categories.value.map(c => c.count)))
</script>

<template>
  <div
    v-if="categories.length"
    class="mb-4 rounded-lg border border-gray-800 bg-gray-900/30 px-3 py-2"
    aria-label="Category breakdown last 7 days"
  >
    <p class="mb-2 text-[10px] font-semibold uppercase tracking-wider text-gray-500">
      Categories · last 7 days
    </p>
    <div class="flex flex-wrap gap-2">
      <span
        v-for="c in categories"
        :key="c.category"
        class="inline-flex items-center gap-1.5 rounded-md bg-gray-950/80 px-2 py-1 text-[11px] ring-1 ring-gray-800"
        :title="`${c.count} insights`"
      >
        <span
          class="h-1.5 rounded-full bg-violet-500"
          :style="{ width: `${Math.max(12, (c.count / max) * 48)}px` }"
        />
        <span class="text-gray-300">{{ c.category.replace(/_/g, ' ') }}</span>
        <span class="font-mono text-gray-500">{{ c.count }}</span>
      </span>
    </div>
  </div>
</template>
