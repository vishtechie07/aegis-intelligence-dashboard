<script setup lang="ts">
import { computed } from 'vue'
import { useInsightStore } from '@/stores/insightStore'

defineProps<{ onRetry?: () => void }>()
const store = useInsightStore()

const visible = computed(
  () => store.bootStatus === 'loading' || store.bootStatus === 'waking-api' || store.bootStatus === 'error',
)

const title = computed(() => {
  if (store.bootStatus === 'error') return 'Could not reach the API'
  if (store.bootStatus === 'waking-api') return 'Starting intelligence engine…'
  return 'Loading intelligence feed…'
})

const detail = computed(() => {
  if (store.bootStatus === 'error') {
    return 'The backend may be offline or still waking up. Check that the API is running, then retry.'
  }
  if (store.bootStatus === 'waking-api') {
    return 'First visit on cloud hosting can take up to a minute while the API cold-starts.'
  }
  return 'Fetching latest insights and opening the live stream.'
})
</script>

<template>
  <div
    v-if="visible"
    class="fixed inset-0 z-40 flex items-center justify-center bg-gray-950/85 p-4 backdrop-blur-sm"
    role="status"
    aria-live="polite"
    :aria-busy="store.bootStatus !== 'error'"
  >
    <div class="w-full max-w-md rounded-xl bg-gray-900 px-6 py-8 text-center shadow-2xl ring-1 ring-gray-700">
      <div
        v-if="store.bootStatus !== 'error'"
        class="mx-auto mb-4 size-10 animate-spin rounded-full border-2 border-gray-700 border-t-blue-500"
      />
      <div v-else class="mx-auto mb-4 text-3xl" aria-hidden="true">⚠</div>
      <h2 class="text-base font-semibold text-white">{{ title }}</h2>
      <p class="mt-2 text-sm text-gray-400">{{ detail }}</p>
      <button
        v-if="store.bootStatus === 'error'"
        type="button"
        class="mt-6 rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-500 transition-colors"
        @click="onRetry?.()"
      >
        Retry
      </button>
    </div>
  </div>
</template>
