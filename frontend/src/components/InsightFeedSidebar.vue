<script setup lang="ts">
import { STARRED_FILTER_TOOLTIP, UNREAD_FILTER_TOOLTIP } from '@/lib/insightLabels'
import { ALL_CATEGORIES, categoryLabel } from '@/lib/categoryLabels'
import type { ThreatFilter } from '@/composables/useFeedFilters'
import type { DateScope, FeedSort, GroupBy, InsightCategory } from '@/types/insight'

defineProps<{
  threatFilter: ThreatFilter
  competitorFilter: string
  categoryFilter: InsightCategory | 'all'
  dateScope: DateScope
  selectedDate: string
  customFrom: string
  customTo: string
  searchQuery: string
  sortBy: FeedSort
  groupBy: GroupBy
  competitors: string[]
  unreadCount: number
}>()

const emit = defineEmits<{
  'update:threatFilter': [ThreatFilter]
  'update:competitorFilter': [string]
  'update:categoryFilter': [InsightCategory | 'all']
  'update:dateScope': [DateScope]
  'update:selectedDate': [string]
  'update:customFrom': [string]
  'update:customTo': [string]
  'update:searchQuery': [string]
  'update:sortBy': [FeedSort]
  'update:groupBy': [GroupBy]
  summary: [string]
  competitorPage: []
  today: []
  yesterday: []
}>()
</script>

<template>
  <aside class="hidden w-52 shrink-0 lg:block">
    <div class="sticky top-20 space-y-1">
      <p class="mb-2 px-2 text-xs font-semibold uppercase tracking-wider text-gray-500">Filter</p>

      <input
        :value="searchQuery"
        type="search"
        placeholder="Search title or summary…"
        class="mb-2 w-full rounded-md border border-gray-700 bg-gray-900 px-2 py-1.5 text-sm text-gray-100 placeholder-gray-500"
        @input="emit('update:searchQuery', ($event.target as HTMLInputElement).value)"
      />

      <button
        class="w-full rounded-md px-3 py-2 text-left text-sm transition-colors"
        :class="threatFilter === 'all' && competitorFilter === 'all' ? 'bg-gray-800 text-white' : 'text-gray-400 hover:bg-gray-900'"
        @click="emit('update:threatFilter', 'all'); emit('update:competitorFilter', 'all')"
      >
        All insights
      </button>
      <button
        class="w-full rounded-md px-3 py-2 text-left text-sm transition-colors"
        :class="threatFilter === 'high' ? 'bg-red-900/60 text-red-300' : 'text-gray-400 hover:bg-gray-900'"
        @click="emit('update:threatFilter', 'high'); emit('update:competitorFilter', 'all')"
      >
        High threat ≥7
      </button>
      <button
        class="w-full rounded-md px-3 py-2 text-left text-sm transition-colors"
        :class="threatFilter === 'starred' ? 'bg-amber-900/50 text-amber-200' : 'text-gray-400 hover:bg-gray-900'"
        :title="STARRED_FILTER_TOOLTIP"
        @click="emit('update:threatFilter', 'starred')"
      >
        Starred
      </button>
      <button
        class="w-full rounded-md px-3 py-2 text-left text-sm transition-colors"
        :class="threatFilter === 'unread' ? 'bg-blue-900/50 text-blue-200' : 'text-gray-400 hover:bg-gray-900'"
        :title="UNREAD_FILTER_TOOLTIP"
        @click="emit('update:threatFilter', 'unread')"
      >
        Unread in feed
        <span v-if="unreadCount" class="ml-1 font-mono text-[10px] text-blue-400">({{ unreadCount }})</span>
      </button>

      <div v-if="competitors.length" class="mt-4">
        <p class="mb-2 px-2 text-xs font-semibold uppercase tracking-wider text-gray-500">Competitors</p>
        <button
          v-for="c in competitors"
          :key="c"
          class="w-full truncate rounded-md px-3 py-2 text-left text-sm transition-colors"
          :class="competitorFilter === c ? 'bg-gray-800 text-white' : 'text-gray-400 hover:bg-gray-900'"
          @click="emit('update:competitorFilter', c)"
        >
          {{ c }}
        </button>
        <button
          v-if="competitorFilter !== 'all'"
          type="button"
          class="mt-1 w-full rounded-md px-3 py-1.5 text-xs text-blue-400 hover:bg-gray-900"
          @click="emit('summary', competitorFilter)"
        >
          Quick summary
        </button>
        <button
          v-if="competitorFilter !== 'all'"
          type="button"
          class="mt-1 w-full rounded-md px-3 py-1.5 text-xs text-violet-400 hover:bg-gray-900"
          @click="emit('competitorPage')"
        >
          Full competitor page →
        </button>
      </div>

      <div class="mt-4 border-t border-gray-800 pt-4 space-y-1">
        <p class="mb-2 px-2 text-xs font-semibold uppercase tracking-wider text-gray-500">Date</p>
        <button
          v-for="d in ([['all', 'All dates'], ['day', 'Single day'], ['7d', 'Last 7 days'], ['30d', 'Last 30 days'], ['custom', 'Custom range']] as const)"
          :key="d[0]"
          class="w-full rounded-md px-3 py-2 text-left text-sm transition-colors"
          :class="dateScope === d[0] ? 'bg-gray-800 text-white' : 'text-gray-400 hover:bg-gray-900'"
          @click="emit('update:dateScope', d[0])"
        >
          {{ d[1] }}
        </button>
        <div v-if="dateScope === 'day'" class="mt-2 space-y-2 px-1">
          <input
            :value="selectedDate"
            type="date"
            class="w-full rounded-md border border-gray-700 bg-gray-900 px-2 py-1.5 text-sm text-gray-200 [color-scheme:dark]"
            @input="emit('update:selectedDate', ($event.target as HTMLInputElement).value)"
          />
          <div class="flex gap-1">
            <button type="button" class="flex-1 rounded-md bg-gray-900 px-2 py-1 text-xs ring-1 ring-gray-700" @click="emit('today')">Today</button>
            <button type="button" class="flex-1 rounded-md bg-gray-900 px-2 py-1 text-xs ring-1 ring-gray-700" @click="emit('yesterday')">Yesterday</button>
          </div>
        </div>
        <div v-if="dateScope === 'custom'" class="mt-2 space-y-2 px-1">
          <label class="block text-[10px] text-gray-500">From</label>
          <input
            :value="customFrom"
            type="date"
            class="w-full rounded-md border border-gray-700 bg-gray-900 px-2 py-1.5 text-sm text-gray-200 [color-scheme:dark]"
            @input="emit('update:customFrom', ($event.target as HTMLInputElement).value)"
          />
          <label class="block text-[10px] text-gray-500">To</label>
          <input
            :value="customTo"
            type="date"
            class="w-full rounded-md border border-gray-700 bg-gray-900 px-2 py-1.5 text-sm text-gray-200 [color-scheme:dark]"
            @input="emit('update:customTo', ($event.target as HTMLInputElement).value)"
          />
        </div>
      </div>

      <div class="mt-4 border-t border-gray-800 pt-4">
        <p class="mb-2 px-2 text-xs font-semibold uppercase tracking-wider text-gray-500">Sort & group</p>
        <select
          :value="sortBy"
          class="mb-2 w-full rounded-md border border-gray-700 bg-gray-900 px-2 py-1.5 text-sm text-gray-200"
          @change="emit('update:sortBy', ($event.target as HTMLSelectElement).value as FeedSort)"
        >
          <option value="processed">Newest processed</option>
          <option value="published">Newest published</option>
          <option value="threat">Highest threat</option>
        </select>
        <select
          :value="groupBy"
          class="w-full rounded-md border border-gray-700 bg-gray-900 px-2 py-1.5 text-sm text-gray-200"
          @change="emit('update:groupBy', ($event.target as HTMLSelectElement).value as GroupBy)"
        >
          <option value="published">Group by published</option>
          <option value="processed">Group by processed</option>
        </select>
      </div>

      <div class="mt-4">
        <p class="mb-2 px-2 text-xs font-semibold uppercase tracking-wider text-gray-500">Category</p>
        <button
          class="w-full rounded-md px-3 py-2 text-left text-sm"
          :class="categoryFilter === 'all' ? 'bg-gray-800 text-white' : 'text-gray-400 hover:bg-gray-900'"
          @click="emit('update:categoryFilter', 'all')"
        >
          All
        </button>
        <button
          v-for="c in ALL_CATEGORIES"
          :key="c"
          class="mt-1 w-full truncate rounded-md px-3 py-2 text-left text-sm"
          :class="categoryFilter === c ? 'bg-gray-800 text-white' : 'text-gray-400 hover:bg-gray-900'"
          @click="emit('update:categoryFilter', c)"
        >
          {{ categoryLabel(c) }}
        </button>
      </div>
    </div>
  </aside>
</template>
