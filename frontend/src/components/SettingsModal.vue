<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useSettingsStore } from '@/stores/settingsStore'

const emit = defineEmits<{ close: [] }>()

const settings = useSettingsStore()

const aiStatusActive = computed(() => settings.isConfigured)
const aiStatusMessage = computed(() => {
  if (settings.isRuntimeKey) return 'AI active — using your OpenAI key'
  if (settings.serverKeyAvailable) {
    return 'AI active — using hosted key on this deployment'
  }
  return 'AI inactive — add your OpenAI key below'
})

const showHostedDemo = computed(() => {
  const q = settings.demoQuota
  return Boolean(q?.trialEnabled && q.usingHostedKey && !settings.isRuntimeKey)
})

const demoSecondsLeft = ref(0)
let countdownTimer: ReturnType<typeof setInterval> | null = null

function syncDemoSecondsFromStore() {
  const q = settings.demoQuota
  if (q && q.trialEnabled && q.usingHostedKey && !settings.isRuntimeKey) {
    demoSecondsLeft.value = Math.max(0, q.secondsRemaining)
  }
}

const demoTimeExpired = computed(() => {
  if (!showHostedDemo.value) return false
  return demoSecondsLeft.value <= 0
})

const demoFullyExpired = computed(() => {
  if (!showHostedDemo.value) return false
  return Boolean(settings.demoQuota?.requiresUserKey)
})

const demoGraceRemaining = computed(() => settings.demoQuota?.askAgentGraceRemaining ?? 0)
const demoGraceTotal = computed(() => settings.demoQuota?.askAgentGraceTotal ?? 0)

const demoTrialCountdown = computed(() => {
  if (demoTimeExpired.value) return null
  const m = Math.floor(demoSecondsLeft.value / 60)
  const s = demoSecondsLeft.value % 60
  return `${m}:${String(s).padStart(2, '0')}`
})

watch(() => settings.demoQuota, syncDemoSecondsFromStore, { deep: true })

onMounted(async () => {
  await settings.checkStatus()
  syncDemoSecondsFromStore()
  countdownTimer = setInterval(() => {
    if (!showHostedDemo.value || demoTimeExpired.value) return
    if (demoSecondsLeft.value > 0) {
      demoSecondsLeft.value--
    }
    if (demoSecondsLeft.value === 0) {
      void settings.checkStatus()
    }
  }, 1000)
})

onUnmounted(() => {
  if (countdownTimer) clearInterval(countdownTimer)
})

const keyInput = ref('')
const showKey = ref(false)
const rememberKey = ref(settings.prefersRememberKey())

watch(() => settings.successMessage, (msg) => {
  if (msg) setTimeout(() => { settings.successMessage = null }, 4000)
})

async function handleSave() {
  const ok = await settings.saveKey(keyInput.value.trim(), rememberKey.value)
  if (ok) keyInput.value = ''
}

function handleKeydown(e: KeyboardEvent) {
  if (e.key === 'Escape') emit('close')
}
</script>

<template>
  <div
    class="fixed inset-0 z-50 flex items-center justify-center bg-black/70 backdrop-blur-sm p-4"
    @click.self="emit('close')"
    @keydown="handleKeydown"
  >
    <div
      class="w-full max-w-md rounded-xl bg-gray-900 shadow-2xl ring-1 ring-gray-700"
      role="dialog"
      aria-modal="true"
      aria-labelledby="settings-title"
      aria-describedby="settings-desc"
    >
      <div class="flex items-center justify-between border-b border-gray-800 px-6 py-4">
        <div>
          <h2 id="settings-title" class="text-base font-semibold text-white">Settings</h2>
          <p id="settings-desc" class="mt-0.5 text-xs text-gray-500">Configure AI agent credentials</p>
        </div>
        <button
          class="rounded-md p-1.5 text-gray-500 hover:bg-gray-800 hover:text-gray-300 transition-colors"
          @click="emit('close')"
          aria-label="Close settings"
        >
          <svg class="size-4" viewBox="0 0 20 20" fill="currentColor">
            <path d="M6.28 5.22a.75.75 0 0 0-1.06 1.06L8.94 10l-3.72 3.72a.75.75 0 1 0 1.06 1.06L10 11.06l3.72 3.72a.75.75 0 1 0 1.06-1.06L11.06 10l3.72-3.72a.75.75 0 0 0-1.06-1.06L10 8.94 6.28 5.22Z"/>
          </svg>
        </button>
      </div>

      <div class="px-6 py-5 space-y-5">

        <div class="flex items-center gap-2 rounded-lg bg-gray-800/60 px-4 py-3 ring-1 ring-gray-700">
          <span
            class="inline-block size-2 shrink-0 rounded-full"
            :class="aiStatusActive ? 'bg-green-400' : 'bg-red-500'"
          />
          <span class="text-sm" :class="aiStatusActive ? 'text-green-300' : 'text-red-300'">
            {{ aiStatusMessage }}
          </span>
        </div>

        <div
          v-if="showHostedDemo"
          class="rounded-lg px-4 py-3 ring-1"
          :class="demoTimeExpired
            ? 'bg-amber-950/40 ring-amber-800/50'
            : 'bg-blue-950/30 ring-blue-800/40'"
        >
          <p class="text-xs font-medium" :class="demoTimeExpired ? 'text-amber-200' : 'text-blue-200'">
            {{
              demoFullyExpired
                ? 'Hosted AI demo ended'
                : demoTimeExpired
                  ? 'Demo time ended — bonus Ask Agent asks'
                  : 'Hosted AI demo (Ask Agent & AI Lookup)'
            }}
          </p>
          <p
            v-if="!demoTimeExpired && demoTrialCountdown"
            class="mt-1 font-mono text-lg font-semibold tabular-nums text-white"
          >
            {{ demoTrialCountdown }}
            <span class="text-sm font-normal text-gray-400">
              remaining of {{ settings.demoQuota?.trialMinutes ?? 5 }} min
            </span>
          </p>
          <p
            v-else-if="demoTimeExpired && !demoFullyExpired"
            class="mt-1 font-mono text-lg font-semibold tabular-nums text-white"
          >
            {{ demoGraceRemaining }}
            <span class="text-sm font-normal text-gray-400">
              of {{ demoGraceTotal }} bonus Ask Agent asks left
            </span>
          </p>
          <p class="mt-1.5 text-xs" :class="demoTimeExpired ? 'text-amber-200/90' : 'text-gray-400'">
            {{
              demoFullyExpired
                ? 'Add your OpenAI key below to keep using Ask Agent and AI Lookup on your own quota.'
                : demoTimeExpired
                  ? 'Ask Agent still works on the hosted key. AI Lookup already needs your own key.'
                  : `News analysis uses the hosted key. After the timer, you get ${demoGraceTotal} bonus Ask Agent asks; AI Lookup needs your key sooner.`
            }}
          </p>
        </div>

        <div>
          <label class="mb-1.5 block text-xs font-medium text-gray-400">
            OpenAI API Key
          </label>
          <div class="relative">
            <input
              v-model="keyInput"
              :type="showKey ? 'text' : 'password'"
              placeholder="sk-..."
              class="w-full rounded-lg bg-gray-800 px-3 py-2.5 pr-10 text-sm text-gray-100 placeholder-gray-600 outline-none ring-1 ring-gray-700 focus:ring-blue-500 font-mono"
              autocomplete="off"
              @keydown.enter="handleSave"
            />
            <button
              class="absolute right-2.5 top-1/2 -translate-y-1/2 text-gray-500 hover:text-gray-300 transition-colors"
              tabindex="-1"
              @click="showKey = !showKey"
              :aria-label="showKey ? 'Hide key' : 'Show key'"
            >
              <svg v-if="!showKey" class="size-4" viewBox="0 0 20 20" fill="currentColor">
                <path d="M10 12.5a2.5 2.5 0 1 0 0-5 2.5 2.5 0 0 0 0 5Z"/>
                <path fill-rule="evenodd" d="M.664 10.59a1.651 1.651 0 0 1 0-1.186A10.004 10.004 0 0 1 10 3c4.257 0 7.893 2.66 9.336 6.41.147.381.146.804 0 1.186A10.004 10.004 0 0 1 10 17c-4.257 0-7.893-2.66-9.336-6.41ZM14 10a4 4 0 1 1-8 0 4 4 0 0 1 8 0Z" clip-rule="evenodd"/>
              </svg>
              <svg v-else class="size-4" viewBox="0 0 20 20" fill="currentColor">
                <path fill-rule="evenodd" d="M3.28 2.22a.75.75 0 0 0-1.06 1.06l14.5 14.5a.75.75 0 1 0 1.06-1.06l-1.745-1.745a10.029 10.029 0 0 0 3.3-4.38 1.651 1.651 0 0 0 0-1.185A10.004 10.004 0 0 0 9.999 3a9.956 9.956 0 0 0-4.744 1.194L3.28 2.22ZM7.752 6.69l1.092 1.092a2.5 2.5 0 0 1 3.374 3.373l1.091 1.092a4 4 0 0 0-5.557-5.557Z" clip-rule="evenodd"/>
                <path d="M10.748 13.93l2.523 2.523a10.003 10.003 0 0 1-8.07-2.873l-.547-.548A10.003 10.003 0 0 1 2.1 9.77l.012-.022a10.01 10.01 0 0 1 2.117-2.836l1.456 1.456a4 4 0 0 0 5.063 5.563Z"/>
              </svg>
            </button>
          </div>
          <label class="mt-3 flex cursor-pointer items-center gap-2 text-xs text-gray-400">
            <input v-model="rememberKey" type="checkbox" class="rounded border-gray-600 bg-gray-800 text-blue-600" />
            Remember key in this browser (uncheck to keep only for this session tab)
          </label>

          <p class="mt-1.5 text-xs text-gray-600">
            Get your key at
            <a href="https://platform.openai.com/api-keys" target="_blank" rel="noopener"
               class="text-blue-500 hover:underline">platform.openai.com/api-keys</a>.
            Saved through your Aegis API only. Your server uses it to call OpenAI — not for news or other data sources.
          </p>
        </div>

        <p v-if="settings.error" class="rounded-md bg-red-950/50 px-3 py-2 text-xs text-red-400 ring-1 ring-red-900">
          {{ settings.error }}
        </p>

        <p v-if="settings.successMessage" class="rounded-md bg-green-950/50 px-3 py-2 text-xs text-green-400 ring-1 ring-green-900">
          {{ settings.successMessage }}
        </p>
      </div>

      <div class="flex items-center justify-between border-t border-gray-800 px-6 py-4">
        <button
          v-if="settings.isRuntimeKey"
          class="text-xs text-gray-600 hover:text-red-400 transition-colors"
          @click="settings.clearKey()"
        >
          Clear saved key
        </button>
        <span v-else />
        <div class="flex gap-3">
          <button
            class="rounded-md px-4 py-2 text-sm text-gray-400 hover:text-gray-200 transition-colors"
            @click="emit('close')"
          >
            Cancel
          </button>
          <button
            class="rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-500 disabled:opacity-50 transition-colors"
            :disabled="settings.isSaving || !keyInput.trim()"
            @click="handleSave"
          >
            {{ settings.isSaving ? 'Saving...' : 'Save Key' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>
