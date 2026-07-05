<script setup lang="ts">
import { computed } from 'vue'
import { useInsightStore } from '@/stores/insightStore'

defineProps<{ onRetry?: () => void }>()
const store = useInsightStore()

const visible = computed(() => store.bootStatus === 'error')

const title = computed(() => 'Could not reach the API')

const detail = computed(
  () => 'Automatic retries were exhausted. The backend may still be waking — wait a moment, then retry.',
)
</script>

<template>
  <div
    v-if="visible"
    class="fixed inset-0 z-40 flex items-center justify-center bg-gray-950/85 p-4 backdrop-blur-sm"
    role="alertdialog"
    aria-live="assertive"
  >
    <div class="w-full max-w-md rounded-xl bg-gray-900 px-6 py-8 text-center shadow-2xl ring-1 ring-gray-700">
      <div class="mx-auto mb-4 text-3xl" aria-hidden="true">⚠</div>
      <h2 class="text-base font-semibold text-white">{{ title }}</h2>
      <p class="mt-2 text-sm text-gray-400">{{ detail }}</p>
      <button
        type="button"
        class="mt-6 rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-500 transition-colors"
        @click="onRetry?.()"
      >
        Retry
      </button>
    </div>
  </div>
</template>
