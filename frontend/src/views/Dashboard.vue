<script setup lang="ts">
import { computed, ref, onMounted, onUnmounted } from 'vue'
import { RouterLink } from 'vue-router'
import { useInsightStore } from '@/stores/insightStore'
import { useSettingsStore } from '@/stores/settingsStore'
import { useCompetitorStore } from '@/stores/competitorStore'
import { useSse } from '@/composables/useSse'
import InsightFeed from '@/components/InsightFeed.vue'
import HarvestStatusBar from '@/components/HarvestStatusBar.vue'
import PipelineStatsBar from '@/components/PipelineStatsBar.vue'
import GlobalAskHistory from '@/components/GlobalAskHistory.vue'
import SettingsModal from '@/components/SettingsModal.vue'
import CompetitorModal from '@/components/CompetitorModal.vue'
import BootLoadingOverlay from '@/components/BootLoadingOverlay.vue'

const { retryBootstrap } = useSse()

const store = useInsightStore()
const settings = useSettingsStore()
const competitors = useCompetitorStore()
const settingsOpen = ref(false)
const competitorsOpen = ref(false)

onMounted(() => {
  settings.restoreFromStorage()
  competitors.fetchAll()
  const quotaTimer = setInterval(() => {
    if (settings.serverKeyAvailable && !settings.isRuntimeKey) {
      void settings.checkStatus()
    }
  }, 30_000)
  onUnmounted(() => clearInterval(quotaTimer))
})

const statusColor = computed(() => ({
  connecting: 'bg-yellow-400',
  connected: 'bg-green-400',
  disconnected: 'bg-red-400',
}[store.connectionStatus]))

const statusLabel = computed(() => {
  if (store.bootStatus === 'waking-api') return 'Waking API…'
  if (store.bootStatus === 'syncing') return 'Syncing feed…'
  if (store.isBootLoading) return 'Connecting…'
  return ({
    connecting: 'Connecting...',
    connected: 'Live',
    disconnected: 'Disconnected',
  } as const)[store.connectionStatus]
})
</script>

<template>
  <div class="min-h-screen bg-gray-950 text-gray-100">
    <header class="sticky top-0 z-10 border-b border-gray-800 bg-gray-950/90 backdrop-blur">
      <div class="mx-auto flex max-w-7xl items-center justify-between px-4 py-3">
        <div class="flex items-center gap-3">
          <span class="text-2xl font-black tracking-tight text-white">
            AEGIS
          </span>
          <span class="hidden text-xs text-gray-500 sm:block">Competitor Intelligence Engine</span>
        </div>

        <div class="flex items-center gap-6">
          <div class="hidden items-center gap-6 text-xs text-gray-400 sm:flex">
            <div class="flex min-w-0 flex-col items-center gap-0.5">
              <span class="font-mono text-base font-semibold leading-none text-white tabular-nums min-h-[1.25rem]">
                {{ store.visibleInsights.length }}
              </span>
              <span class="whitespace-nowrap text-center">in feed</span>
            </div>
            <div class="flex min-w-0 flex-col items-center gap-0.5">
              <span class="font-mono text-base font-semibold leading-none text-gray-300 tabular-nums min-h-[1.25rem]">
                {{ store.stats?.totalInsights?.toLocaleString() ?? '—' }}
              </span>
              <span class="whitespace-nowrap text-center">analyzed</span>
            </div>
            <div class="flex min-w-0 flex-col items-center gap-0.5">
              <span class="font-mono text-base font-semibold leading-none text-red-400 tabular-nums min-h-[1.25rem]">
                {{ store.stats?.highThreatCount?.toLocaleString() ?? store.highThreatInsights.length }}
              </span>
              <span class="whitespace-nowrap text-center">high-threat</span>
            </div>
            <div class="flex min-w-0 flex-col items-center gap-0.5">
              <span class="font-mono text-base font-semibold leading-none text-blue-400 tabular-nums min-h-[1.25rem]">{{ store.insightsByCompetitor.size }}</span>
              <span class="whitespace-nowrap text-center">competitors</span>
            </div>
          </div>

          <div class="flex items-center gap-2">
            <span
              class="inline-block size-2 rounded-full"
              :class="[
                store.isBootLoading ? 'bg-yellow-400 animate-pulse' : statusColor,
                !store.isBootLoading && store.connectionStatus === 'connected' ? 'animate-pulse' : '',
              ]"
            />
            <span class="text-xs text-gray-400">{{ statusLabel }}</span>
          </div>

          <RouterLink
            to="/help"
            class="flex min-w-[132px] items-center justify-center gap-1.5 rounded-md px-2.5 py-1.5 text-xs text-gray-400 ring-1 ring-gray-700 hover:bg-gray-800 hover:text-gray-200 transition-colors"
            title="How to use this dashboard"
          >
            <svg class="size-3.5" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
              <path fill-rule="evenodd" d="M18 10a8 8 0 1 1-16 0 8 8 0 0 1 16 0ZM8.94 6.94a.75.75 0 1 1-1.061-1.061 3 3 0 1 1 2.871 5.026v.345a.75.75 0 0 1-1.5 0v-.5c0-.69.56-1.25 1.25-1.25H10a.75.75 0 0 1 0 1.5h-.086a1.5 1.5 0 0 0-1.037-3.61ZM10 15a1 1 0 1 0 0-2 1 1 0 0 0 0 2Z" clip-rule="evenodd"/>
            </svg>
            <span class="hidden sm:inline">Guide</span>
          </RouterLink>

          <button
            class="flex min-w-[132px] items-center justify-center gap-1.5 rounded-md px-2.5 py-1.5 text-xs text-gray-400 ring-1 ring-gray-700 hover:bg-gray-800 hover:text-gray-200 transition-colors"
            title="Manage tracked competitors"
            @click="competitorsOpen = true"
          >
            <svg class="size-3.5" viewBox="0 0 20 20" fill="currentColor">
              <path d="M10 9a3 3 0 1 0 0-6 3 3 0 0 0 0 6ZM6 8a2 2 0 1 1-4 0 2 2 0 0 1 4 0ZM1.49 15.326a.78.78 0 0 1-.358-.442 3 3 0 0 1 4.308-3.516 6.484 6.484 0 0 0-1.905 3.959c-.023.222-.014.442.025.654a4.97 4.97 0 0 1-2.07-.655ZM16.44 15.98a4.97 4.97 0 0 0 2.07-.654.78.78 0 0 0 .357-.442 3 3 0 0 0-4.308-3.517 6.484 6.484 0 0 1 1.907 3.96 2.32 2.32 0 0 1-.026.654ZM18 8a2 2 0 1 1-4 0 2 2 0 0 1 4 0ZM5.304 16.19a.844.844 0 0 1-.277-.71 5 5 0 0 1 9.947 0 .843.843 0 0 1-.277.71A6.975 6.975 0 0 1 10 18a6.974 6.974 0 0 1-4.696-1.81Z"/>
            </svg>
            <span class="hidden sm:inline">Competitors</span>
            <span class="rounded-full bg-gray-700 px-1.5 py-0.5 text-[10px] font-mono text-gray-300">{{ competitors.list.length }}</span>
          </button>

          <button
            class="flex min-w-[132px] items-center justify-center gap-1.5 rounded-md px-2.5 py-1.5 text-xs transition-colors ring-1"
            :class="settings.isConfigured
              ? 'text-gray-400 ring-gray-700 hover:bg-gray-800 hover:text-gray-200'
              : 'text-amber-300 ring-amber-700/50 bg-amber-950/40 hover:bg-amber-900/40 animate-pulse'"
            :title="settings.isConfigured ? 'Settings' : 'OpenAI key required — click to configure'"
            @click="settingsOpen = true"
          >
            <svg class="size-3.5" viewBox="0 0 20 20" fill="currentColor">
              <path fill-rule="evenodd" d="M7.84 1.804A1 1 0 0 1 8.82 1h2.36a1 1 0 0 1 .98.804l.331 1.652a6.993 6.993 0 0 1 1.929 1.115l1.598-.54a1 1 0 0 1 1.186.447l1.18 2.044a1 1 0 0 1-.205 1.251l-1.267 1.113a7.047 7.047 0 0 1 0 2.228l1.267 1.113a1 1 0 0 1 .206 1.25l-1.18 2.045a1 1 0 0 1-1.187.447l-1.598-.54a6.993 6.993 0 0 1-1.929 1.115l-.33 1.652a1 1 0 0 1-.98.804H8.82a1 1 0 0 1-.98-.804l-.331-1.652a6.993 6.993 0 0 1-1.929-1.115l-1.598.54a1 1 0 0 1-1.186-.447l-1.18-2.044a1 1 0 0 1 .205-1.251l1.267-1.114a7.05 7.05 0 0 1 0-2.227L1.821 7.773a1 1 0 0 1-.206-1.25l1.18-2.045a1 1 0 0 1 1.187-.447l1.598.54A6.992 6.992 0 0 1 7.51 3.456l.33-1.652ZM10 13a3 3 0 1 0 0-6 3 3 0 0 0 0 6Z" clip-rule="evenodd"/>
            </svg>
            <span class="hidden sm:inline">{{ settings.isConfigured ? 'Settings' : 'Add API Key' }}</span>
          </button>
        </div>
      </div>
    </header>

    <main class="mx-auto max-w-7xl px-4 py-6">
      <div
        v-if="store.isBootLoading"
        class="mb-4 flex items-center gap-3 rounded-lg border border-blue-900/50 bg-blue-950/30 px-4 py-3"
        role="status"
        aria-live="polite"
      >
        <span class="inline-block size-2 shrink-0 animate-pulse rounded-full bg-blue-400" />
        <p class="text-sm text-blue-200">
          <template v-if="store.bootStatus === 'waking-api'">
            Starting intelligence engine — cloud API cold start can take up to two minutes.
          </template>
          <template v-else-if="store.bootStatus === 'syncing'">
            API is up. Loading insights and opening the live stream…
          </template>
          <template v-else>
            Connecting to the intelligence engine…
          </template>
        </p>
      </div>

      <div
        v-if="!settings.isConfigured"
        class="mb-4 flex items-center gap-3 rounded-lg bg-amber-950/40 px-4 py-3 ring-1 ring-amber-700/40"
      >
        <span class="text-amber-400">⚠</span>
        <p class="text-sm text-amber-300">
          AI agents are inactive. News is being harvested but won't be analysed until you
          <button class="underline hover:text-amber-100 transition-colors" @click="settingsOpen = true">add your OpenAI key</button>.
        </p>
      </div>

      <div
        v-if="settings.demoQuota?.requiresUserKey && settings.serverKeyAvailable && !settings.isRuntimeKey"
        class="mb-4 rounded-lg bg-amber-950/40 px-4 py-3 ring-1 ring-amber-700/40"
      >
        <p class="text-sm text-amber-200">
          Hosted AI demo time is up. Add your OpenAI key in
          <button class="underline hover:text-amber-50" @click="settingsOpen = true">Settings</button>
          to use Ask Agent and AI Lookup.
        </p>
      </div>

      <div
        v-if="settings.aiKeyIssueBanner"
        class="mb-4 flex items-start justify-between gap-3 rounded-lg bg-red-950/40 px-4 py-3 ring-1 ring-red-800/50"
      >
        <p class="text-sm text-red-200">{{ settings.aiKeyIssueBanner }}</p>
        <button
          type="button"
          class="shrink-0 text-xs text-red-400 underline hover:text-red-200"
          @click="settings.dismissAiKeyIssue()"
        >
          Dismiss
        </button>
      </div>

      <HarvestStatusBar />
      <PipelineStatsBar />
      <GlobalAskHistory />

      <p class="mb-3 text-xs text-gray-500">
        <span class="inline-block border-l-4 border-cyan-400 pl-2">Colored left border</span>
        identifies the competitor;
        <span class="ml-2 inline-flex items-center rounded bg-red-500/20 px-1.5 py-0.5 font-mono text-[10px] text-red-300 ring-1 ring-red-500/30">Threat</span>
        badge is strategic severity (1–10).
      </p>

      <InsightFeed />
    </main>

    <BootLoadingOverlay :on-retry="retryBootstrap" />

    <Teleport to="body">
      <SettingsModal v-if="settingsOpen" @close="settingsOpen = false" />
      <CompetitorModal v-if="competitorsOpen" @close="competitorsOpen = false" />
    </Teleport>
  </div>
</template>
