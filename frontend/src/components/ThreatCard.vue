<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'
import { fetchRelated } from '@/composables/useInsightFeed'
import { aegisSessionHeaders } from '@/composables/useAegisSession'
import { sourceIcon, sourceLabel, threatLabel, THREAT_TOOLTIP } from '@/lib/insightLabels'
import { useInsightStore } from '@/stores/insightStore'
import { useSettingsStore } from '@/stores/settingsStore'
import type { Insight, DeepDiveRequest, DeepDiveResponse, DeepDiveHistoryEntry, DeepDiveSource, RelatedInsightBrief } from '@/types/insight'

const props = defineProps<{ insight: Insight }>()

const API_BASE = import.meta.env.VITE_API_BASE_URL ?? ''
const settings = useSettingsStore()
const insightStore = useInsightStore()

const KNOWN_COMPETITOR_BORDERS: { test: (n: string) => boolean; cls: string }[] = [
  { test: n => /\bopenai\b/i.test(n), cls: 'border-emerald-400' },
  { test: n => /\b(google|alphabet)\b/i.test(n), cls: 'border-blue-400' },
  { test: n => /\bmicrosoft\b/i.test(n), cls: 'border-sky-400' },
  { test: n => /\bamazon\b/i.test(n), cls: 'border-amber-400' },
  { test: n => /\banthropic\b/i.test(n), cls: 'border-violet-400' },
  { test: n => /\bmeta\b/i.test(n), cls: 'border-indigo-400' },
  { test: n => /\bapple\b/i.test(n), cls: 'border-gray-300' },
]

const competitorBorder = computed(() => {
  const palette = [
    'border-cyan-400',
    'border-violet-400',
    'border-emerald-400',
    'border-amber-400',
    'border-pink-400',
    'border-sky-400',
    'border-lime-400',
    'border-fuchsia-400',
  ]
  const name = props.insight.competitorName ?? ''
  const lower = name.toLowerCase()
  for (const k of KNOWN_COMPETITOR_BORDERS) {
    if (k.test(lower)) return k.cls
  }
  let hash = 0
  for (let i = 0; i < name.length; i++) hash = (hash * 31 + name.charCodeAt(i)) | 0
  const idx = Math.abs(hash) % palette.length
  return palette[idx]
})

const threatBadge = computed(() => {
  if (props.insight.threatLevel >= 9) return 'bg-red-500/20 text-red-300 ring-red-500/30'
  if (props.insight.threatLevel >= 7) return 'bg-orange-500/20 text-orange-300 ring-orange-500/30'
  if (props.insight.threatLevel >= 5) return 'bg-amber-400/20 text-amber-200 ring-amber-400/30'
  return 'bg-green-500/20 text-green-300 ring-green-500/30'
})

const categoryLabel = computed(() => {
  const map: Record<string, string> = {
    PRODUCT_LAUNCH: 'Product Launch',
    HIRING: 'Hiring',
    FINANCIAL_MOVE: 'Financial',
    PARTNERSHIP: 'Partnership',
    LEGAL: 'Legal',
    LEADERSHIP_CHANGE: 'Leadership',
    OTHER: 'Other',
  }
  return props.insight.category ? (map[props.insight.category] ?? props.insight.category) : null
})

function fmtWhen(iso: string) {
  return new Date(iso).toLocaleString(undefined, { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' })
}

const timestampLines = computed(() => {
  const pub = props.insight.publishedAt
  const proc = props.insight.processedAt
  const lines: { label: string; text: string }[] = []
  if (pub) lines.push({ label: 'Published', text: fmtWhen(pub) })
  if (proc) lines.push({ label: 'Processed', text: fmtWhen(proc) })
  return lines
})

const sourceIconChar = computed(() => sourceIcon(props.insight.sourceType))
const sourceName = computed(() => sourceLabel(props.insight.sourceType))
const threatTier = computed(() => threatLabel(props.insight.threatLevel))

const excerptOpen = ref(false)
const relatedStories = ref<RelatedInsightBrief[]>([])
const relatedOpen = ref(false)

onMounted(() => {
  insightStore.markRead(props.insight.id)
  if (props.insight.ragAvailable) {
    void fetchRelated(props.insight.newsId, 3).then(r => { relatedStories.value = r })
  }
})

const deepDiveOpen = ref(false)
const deepDiveQuestion = ref('')
const deepDiveResult = ref('')
const deepDiveSources = ref<DeepDiveSource[]>([])
const deepDiveRagUsed = ref(false)
const sourcesOpen = ref(true)
const deepDiveLoading = ref(false)
const deepDiveCopied = ref(false)
const deepDiveInputEl = ref<HTMLInputElement | null>(null)
const deepDiveHistory = ref<DeepDiveHistoryEntry[]>([])
const deepDiveHistoryLoading = ref(false)
const selectedHistoryId = ref<number | null>(null)

function loadHistoryEntry(entry: DeepDiveHistoryEntry) {
  selectedHistoryId.value = entry.id
  deepDiveQuestion.value = entry.question
  deepDiveResult.value = sanitizeDeepDive(entry.analysis)
  deepDiveSources.value = entry.sources ?? []
  deepDiveRagUsed.value = entry.ragUsed ?? false
  sourcesOpen.value = true
  deepDiveCopied.value = false
}

function historyPreview(analysis: string): string {
  const first = sanitizeDeepDive(analysis).split('\n').map(l => l.trim()).find(l => l && !/^answer:/i.test(l))
  if (!first) return ''
  return first.length > 120 ? `${first.slice(0, 120)}…` : first
}

watch(deepDiveOpen, async (open) => {
  if (!open) {
    selectedHistoryId.value = null
    return
  }
  deepDiveHistoryLoading.value = true
  try {
    const res = await fetch(`${API_BASE}/api/insights/deep-dive/history?newsId=${props.insight.newsId}`)
    deepDiveHistory.value = res.ok ? await res.json() : []
    if (deepDiveHistory.value.length > 0) {
      loadHistoryEntry(deepDiveHistory.value[0])
    }
  } catch {
    deepDiveHistory.value = []
  } finally {
    deepDiveHistoryLoading.value = false
  }
  await nextTick()
  deepDiveInputEl.value?.focus()
})

function sanitizeDeepDive(text: string): string {
  if (!text) return ''
  let t = text.replace(/\r\n/g, '\n')
  t = t.replace(/^\s*#{1,6}\s*/gm, '')
  // Some models prefix headings with a bullet.
  t = t.replace(/^\s*•\s*#{1,6}\s*/gm, '')
  t = t.replace(/\*\*/g, '')
  t = t.replace(/`{1,3}/g, '')
  t = t.replace(/^\s*[-*]\s*/gm, '• ')
  return t.trim()
}

type DeepDiveParsed = {
  answer: string
  strategicImplications: string[]
  recommendedActions: string[]
}

function parseDeepDive(text: string): DeepDiveParsed | null {
  const t = sanitizeDeepDive(text)
  if (!t) return null

  const lines = t.split('\n').map(l => l.trim()).filter(Boolean)
  if (lines.length === 0) return null

  let section: 'answer' | 'strategic' | 'recommended' = 'answer'
  const answerParts: string[] = []
  const strategic: string[] = []
  const recommended: string[] = []

  const isStrategicHeader = (s: string) => /strategic implications/i.test(s)
  const isRecommendedHeader = (s: string) => /recommended actions/i.test(s) || /recommended action/i.test(s)
  const isAnswerHeader = (s: string) => /(^answer:)|direct answer/i.test(s)

  for (const raw of lines) {
    const line = raw.replace(/^•\s*/, '')

    if (isStrategicHeader(line) || /^strategic$/i.test(line)) {
      section = 'strategic'
      continue
    }
    if (isRecommendedHeader(line) || /^recommended$/i.test(line)) {
      section = 'recommended'
      continue
    }
    if (isAnswerHeader(line)) {
      section = 'answer'
      continue
    }

    const isBullet = /^\s*•\s*/.test(raw)
    if (isBullet) {
      const bulletText = line.trim()
      if (!bulletText) continue
      if (section === 'strategic') strategic.push(bulletText)
      else if (section === 'recommended') recommended.push(bulletText)
      else answerParts.push(bulletText)
      continue
    }

    if (section === 'answer') answerParts.push(raw)
    else if (section === 'strategic') strategic.push(raw)
    else recommended.push(raw)
  }

  return {
    answer: answerParts.join('\n').trim(),
    strategicImplications: strategic,
    recommendedActions: recommended,
  }
}

const deepDiveParsed = computed(() => {
  return deepDiveResult.value ? parseDeepDive(deepDiveResult.value) : null
})

async function copyDeepDive() {
  if (!deepDiveResult.value) return
  try {
    await navigator.clipboard.writeText(deepDiveResult.value)
    deepDiveCopied.value = true
    window.setTimeout(() => (deepDiveCopied.value = false), 2000)
  } catch {
    // best-effort: ignore clipboard failures
  }
}

async function submitDeepDive() {
  if (!deepDiveQuestion.value.trim()) return
  deepDiveLoading.value = true
  deepDiveResult.value = ''
  deepDiveSources.value = []
  deepDiveRagUsed.value = false
  selectedHistoryId.value = null
  deepDiveCopied.value = false
  try {
    const body: DeepDiveRequest = { newsId: props.insight.newsId, question: deepDiveQuestion.value }
    const res = await fetch(`${API_BASE}/api/insights/deep-dive`, {
      method: 'POST',
      headers: aegisSessionHeaders({ 'Content-Type': 'application/json' }),
      body: JSON.stringify(body),
    })
    const data = (await res.json().catch(() => ({}))) as DeepDiveResponse & { error?: string }
    if (res.status === 402) {
      const msg = data.error ?? 'Demo ended — add your OpenAI key in Settings.'
      settings.reportAiKeyIssue(msg)
      deepDiveResult.value = msg
      return
    }
    if (!res.ok) {
      deepDiveResult.value = data.error ?? 'Analysis failed. Please try again.'
      return
    }
    deepDiveResult.value = sanitizeDeepDive(data.analysis ?? '')
    deepDiveSources.value = data.sources ?? []
    deepDiveRagUsed.value = data.ragUsed ?? false
    sourcesOpen.value = true
    void settings.checkStatus()
    const h = await fetch(`${API_BASE}/api/insights/deep-dive/history?newsId=${props.insight.newsId}`)
    if (h.ok) {
      deepDiveHistory.value = await h.json()
      if (deepDiveHistory.value.length > 0) {
        selectedHistoryId.value = deepDiveHistory.value[0].id
      }
    }
  } catch {
    deepDiveResult.value = 'Analysis failed. Please try again.'
  } finally {
    deepDiveLoading.value = false
  }
}
</script>

<template>
  <div
    class="mb-3 rounded-lg border-l-4 bg-gray-900 shadow-sm transition-all duration-500"
    :class="[
      competitorBorder,
      insight.isNew ? 'animate-pulse ring-1 ring-blue-500/50' : '',
      insightStore.isRead(insight.id) && !insight.isNew ? 'opacity-90' : '',
    ]"
  >
    <div class="p-4">
      <div class="flex flex-wrap items-start justify-between gap-2">
        <div class="flex items-center gap-2">
          <span v-if="!insightStore.isRead(insight.id)" class="size-2 shrink-0 rounded-full bg-blue-500" title="Unread in loaded feed" />
          <RouterLink
            :to="`/competitor/${encodeURIComponent(insight.competitorName)}`"
            class="font-bold text-white hover:text-blue-400 transition-colors"
          >
            {{ insight.competitorName }}
          </RouterLink>
          <span
            v-if="categoryLabel"
            class="rounded px-1.5 py-0.5 text-xs font-medium text-gray-300 ring-1 ring-gray-700 bg-gray-800"
          >
            {{ categoryLabel }}
          </span>
        </div>

        <div class="flex items-center gap-2">
          <button
            type="button"
            class="text-sm transition-colors"
            :class="insightStore.isStarred(insight.id) ? 'text-amber-400' : 'text-gray-600 hover:text-amber-300'"
            :title="insightStore.isStarred(insight.id) ? 'Unstar' : 'Star'"
            @click="insightStore.toggleStar(insight.id)"
          >
            {{ insightStore.isStarred(insight.id) ? '★' : '☆' }}
          </button>
          <button
            type="button"
            class="text-xs text-gray-600 hover:text-gray-400"
            title="Dismiss"
            @click="insightStore.dismiss(insight.id)"
          >
            ✕
          </button>
          <span class="text-xs" :title="sourceName">{{ sourceIconChar }}</span>
          <span class="hidden text-[10px] text-gray-500 sm:inline">{{ sourceName }}</span>
          <span
            v-if="insight.ragAvailable"
            class="rounded bg-violet-500/15 px-1 py-0.5 text-[9px] font-semibold uppercase text-violet-300 ring-1 ring-violet-500/25"
            title="Related intel available for Ask Agent"
          >
            RAG
          </span>
          <span
            class="rounded px-2 py-0.5 text-xs font-mono ring-1"
            :class="threatBadge"
            :title="THREAT_TOOLTIP"
          >
            {{ threatTier }} {{ insight.threatLevel }}/10
          </span>
          <span class="flex flex-col items-end gap-0.5 text-right text-[10px] leading-tight text-gray-500">
            <span v-for="(line, idx) in timestampLines" :key="idx">{{ line.label }}: {{ line.text }}</span>
          </span>
        </div>
      </div>

      <a
        :href="insight.sourceUrl ?? '#'"
        target="_blank"
        rel="noopener noreferrer"
        class="mt-2 block text-sm font-medium text-gray-200 hover:text-blue-400 transition-colors"
      >
        {{ insight.title }}
      </a>

      <p class="mt-2 text-sm text-gray-400">{{ insight.summary }}</p>

      <p v-if="insight.agentName" class="mt-1 text-[10px] text-gray-600">
        Analyzed by <span class="text-gray-500">{{ insight.agentName }}</span>
      </p>

      <div v-if="insight.contentExcerpt" class="mt-2">
        <button
          type="button"
          class="text-xs text-gray-500 hover:text-gray-300"
          @click="excerptOpen = !excerptOpen"
        >
          {{ excerptOpen ? 'Hide' : 'Show' }} original excerpt
        </button>
        <p v-if="excerptOpen" class="mt-1 rounded bg-gray-950/80 p-2 text-xs leading-relaxed text-gray-500 ring-1 ring-gray-800 whitespace-pre-wrap">
          {{ insight.contentExcerpt }}
        </p>
      </div>

      <div class="mt-3 rounded border-l-2 border-blue-500/50 bg-blue-950/40 px-3 py-2 text-xs text-blue-200 ring-1 ring-blue-900/40">
        <span class="font-semibold text-blue-400">Strategy · </span>{{ insight.strategicAdvice }}
      </div>

      <div v-if="relatedStories.length" class="mt-2">
        <button
          type="button"
          class="text-xs text-gray-500 hover:text-gray-300"
          @click="relatedOpen = !relatedOpen"
        >
          {{ relatedOpen ? 'Hide' : 'Show' }} {{ relatedStories.length }} semantically related
          <span class="text-violet-400/80">(RAG)</span>
        </button>
        <ul v-if="relatedOpen" class="mt-1 space-y-1">
          <li
            v-for="r in relatedStories"
            :key="r.newsId"
            class="rounded bg-gray-950/60 px-2 py-1 text-xs ring-1 ring-gray-800"
          >
            <a
              v-if="r.sourceUrl"
              :href="r.sourceUrl"
              target="_blank"
              rel="noopener noreferrer"
              class="text-blue-400 hover:underline"
            >{{ r.title }}</a>
            <span v-else class="text-gray-300">{{ r.title }}</span>
          </li>
        </ul>
      </div>

      <div class="mt-3">
        <button
          class="rounded-md bg-gray-800 px-3 py-1.5 text-xs font-medium text-gray-300 hover:bg-gray-700 hover:text-white transition-colors ring-1 ring-gray-700 disabled:opacity-60 disabled:cursor-not-allowed"
          :disabled="deepDiveLoading"
          @click="deepDiveOpen = !deepDiveOpen"
        >
          {{ deepDiveOpen ? 'Close' : 'Ask Agent' }}
        </button>
      </div>
    </div>

    <Transition name="slide">
      <div v-if="deepDiveOpen" class="border-t border-gray-800 bg-gray-950/50 p-4">
        <div v-if="deepDiveHistoryLoading" class="mb-3 text-xs text-gray-500">Loading history…</div>
        <div v-else-if="deepDiveHistory.length" class="mb-3 max-h-48 space-y-1 overflow-y-auto rounded-md bg-gray-900/80 p-2 ring-1 ring-gray-800">
          <p class="mb-1 text-[10px] font-semibold uppercase tracking-wider text-gray-500">Previous asks</p>
          <button
            v-for="h in deepDiveHistory"
            :key="h.id"
            type="button"
            class="w-full rounded-md px-2 py-2 text-left text-xs transition-colors"
            :class="selectedHistoryId === h.id
              ? 'bg-blue-950/50 ring-1 ring-blue-800'
              : 'hover:bg-gray-800/80'"
            @click="loadHistoryEntry(h)"
          >
            <div class="flex items-start justify-between gap-2">
              <p class="font-medium text-gray-300">{{ h.question }}</p>
              <span
                v-if="selectedHistoryId === h.id"
                class="shrink-0 text-[10px] font-medium text-blue-400"
              >
                Viewing
              </span>
            </div>
            <p class="mt-0.5 line-clamp-1 text-gray-500">{{ historyPreview(h.analysis) }}</p>
            <p v-if="h.ragUsed || (h.sources?.length ?? 0) > 0" class="mt-0.5 text-[10px] text-gray-600">
              {{ h.ragUsed ? 'RAG' : 'Sources' }} · {{ h.sources?.length ?? 0 }} cited
            </p>
          </button>
        </div>
        <div class="flex gap-2">
          <input
            v-model="deepDiveQuestion"
            ref="deepDiveInputEl"
            type="text"
            placeholder="Ask how this news affects strategy, risk, or your response…"
            class="flex-1 rounded-md bg-gray-800 px-3 py-2 text-sm text-gray-100 placeholder-gray-500 outline-none ring-1 ring-gray-700 focus:ring-blue-500"
            @keydown.enter="submitDeepDive"
          />
          <button
            class="rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-500 disabled:opacity-50 transition-colors"
            :disabled="deepDiveLoading || !deepDiveQuestion.trim()"
            @click="submitDeepDive"
          >
            {{ deepDiveLoading ? '...' : 'Ask' }}
          </button>
        </div>

        <div v-if="deepDiveLoading" class="mt-3 max-h-80 overflow-y-auto rounded-md bg-gray-900 p-3 ring-1 ring-gray-800" aria-live="polite">
          <div class="flex items-center justify-between gap-3">
            <div class="text-xs text-gray-500">Deep-dive response</div>
            <div class="text-xs text-gray-400 animate-pulse">Thinking...</div>
          </div>
          <div class="mt-3 space-y-2">
            <div class="h-3 w-3/4 rounded bg-gray-800 animate-pulse" />
            <div class="h-3 w-5/6 rounded bg-gray-800 animate-pulse" />
            <div class="h-3 w-2/3 rounded bg-gray-800 animate-pulse" />
          </div>
        </div>

        <div v-else-if="deepDiveParsed" class="mt-3">
          <div class="flex items-center justify-between gap-3">
            <div class="text-xs text-gray-500">Deep-dive response</div>
            <button
              class="rounded-md bg-gray-800 px-3 py-1.5 text-xs text-gray-200 ring-1 ring-gray-700 hover:bg-gray-700 transition-colors disabled:opacity-60"
              :disabled="!deepDiveResult || deepDiveLoading"
              @click="copyDeepDive"
            >
              {{ deepDiveCopied ? 'Copied' : 'Copy' }}
            </button>
          </div>

          <div class="mt-2 max-h-80 overflow-y-auto rounded-md bg-gray-900 p-3 text-sm leading-relaxed text-gray-300 ring-1 ring-gray-800" aria-live="polite">
            <div v-if="deepDiveParsed.answer" class="whitespace-pre-wrap mb-3">
              <div class="text-xs font-semibold text-gray-200 mb-1">Answer</div>
              <div>{{ deepDiveParsed.answer }}</div>
            </div>

            <div v-if="deepDiveParsed.strategicImplications.length" class="mb-3">
              <div class="text-xs font-semibold text-gray-200 mb-1">Strategic implications</div>
              <ul class="list-disc list-inside space-y-1">
                <li v-for="(b, idx) in deepDiveParsed.strategicImplications" :key="idx">{{ b }}</li>
              </ul>
            </div>

            <div v-if="deepDiveParsed.recommendedActions.length">
              <div class="text-xs font-semibold text-gray-200 mb-1">Recommended actions</div>
              <ul class="list-disc list-inside space-y-1">
                <li v-for="(b, idx) in deepDiveParsed.recommendedActions" :key="idx">{{ b }}</li>
              </ul>
            </div>
          </div>

          <div v-if="deepDiveSources.length" class="mt-3 border-t border-gray-800 pt-3">
            <button
              type="button"
              class="flex w-full items-center justify-between gap-2 text-left text-xs font-medium text-gray-300 hover:text-white"
              @click="sourcesOpen = !sourcesOpen"
            >
              <span>
                Sources used ({{ deepDiveSources.length }})
                <span
                  v-if="deepDiveRagUsed"
                  class="ml-2 rounded bg-violet-500/20 px-1.5 py-0.5 text-[10px] font-semibold uppercase tracking-wide text-violet-300 ring-1 ring-violet-500/30"
                >
                  RAG
                </span>
              </span>
              <span class="text-gray-500">{{ sourcesOpen ? '▾' : '▸' }}</span>
            </button>
            <ul v-show="sourcesOpen" class="mt-2 space-y-2">
              <li
                v-for="s in deepDiveSources"
                :key="`${s.newsId}-${s.currentArticle}`"
                class="rounded-md p-2 ring-1"
                :class="s.currentArticle ? 'bg-blue-950/30 ring-blue-900/50' : 'bg-gray-900/60 ring-gray-800'"
              >
                <div class="flex items-center gap-2">
                  <span
                    class="rounded px-1 py-0.5 text-[10px] font-semibold uppercase tracking-wide"
                    :class="s.currentArticle ? 'bg-blue-500/20 text-blue-300' : 'bg-gray-800 text-gray-400'"
                  >
                    {{ s.currentArticle ? 'This article' : 'Related' }}
                  </span>
                </div>
                <a
                  v-if="s.sourceUrl"
                  :href="s.sourceUrl"
                  target="_blank"
                  rel="noopener noreferrer"
                  class="mt-1 block text-xs font-medium text-blue-400 hover:text-blue-300"
                >
                  {{ s.title }}
                </a>
                <p v-else class="mt-1 text-xs font-medium text-gray-200">{{ s.title }}</p>
                <p v-if="s.excerpt" class="mt-1 line-clamp-3 text-[11px] leading-relaxed text-gray-500">
                  {{ s.excerpt }}
                </p>
              </li>
            </ul>
          </div>
        </div>

        <div
          v-else-if="deepDiveResult"
          class="mt-3 max-h-80 overflow-y-auto whitespace-pre-wrap break-words rounded-md bg-gray-900 p-3 text-sm leading-relaxed text-gray-300 ring-1 ring-gray-800"
          aria-live="polite"
        >
          <div class="flex items-center justify-between gap-3 mb-2">
            <div class="text-xs text-gray-500">Deep-dive response</div>
            <button
              class="rounded-md bg-gray-800 px-3 py-1.5 text-xs text-gray-200 ring-1 ring-gray-700 hover:bg-gray-700 transition-colors disabled:opacity-60"
              :disabled="!deepDiveResult || deepDiveLoading"
              @click="copyDeepDive"
            >
              {{ deepDiveCopied ? 'Copied' : 'Copy' }}
            </button>
          </div>
          {{ deepDiveResult }}
        </div>
      </div>
    </Transition>
  </div>
</template>

<style scoped>
.slide-enter-active,
.slide-leave-active {
  transition: all 0.2s ease;
  overflow: hidden;
}
.slide-enter-from,
.slide-leave-to {
  max-height: 0;
  opacity: 0;
}
.slide-enter-to,
.slide-leave-from {
  max-height: 1200px;
  opacity: 1;
}
</style>
