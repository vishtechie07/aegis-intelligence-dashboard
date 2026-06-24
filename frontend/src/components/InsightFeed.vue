<script setup lang="ts">
import { computed } from 'vue'
import { useFeedFilters } from '@/composables/useFeedFilters'
import { useFeedGrouping } from '@/composables/useFeedGrouping'
import { useInsightStore } from '@/stores/insightStore'
import { useCompetitorStore } from '@/stores/competitorStore'
import { useSettingsStore } from '@/stores/settingsStore'
import CategorySummaryStrip from './CategorySummaryStrip.vue'
import CompetitorSummaryPanel from './CompetitorSummaryPanel.vue'
import InsightAnalyticsPanel from './InsightAnalyticsPanel.vue'
import InsightFeedList from './InsightFeedList.vue'
import InsightFeedSidebar from './InsightFeedSidebar.vue'

const store = useInsightStore()
const settings = useSettingsStore()
const competitorsStore = useCompetitorStore()

const {
  feedLoading,
  feedError,
  summaryCompetitor,
  threatFilter,
  competitorFilter,
  categoryFilter,
  dateScope,
  selectedDate,
  customFrom,
  customTo,
  searchQuery,
  sortBy,
  groupBy,
  filtered,
  feedCountLabel,
  activeFilterSummary,
  reloadFeed,
  openCompetitorPage,
  setToday,
  setYesterday,
} = useFeedFilters()

const competitors = computed(() => {
  const fromStore = competitorsStore.list.map(c => c.name)
  if (fromStore.length) return [...fromStore].sort()
  return [...store.insightsByCompetitor.keys()].sort()
})

const { groupedByDay } = useFeedGrouping(filtered, groupBy)

const emptyFeedMessage = computed(() => {
  if (feedError.value) return 'Could not load insights. Check API connection and retry.'
  if (!settings.isConfigured) {
    return 'AI agents are inactive. News is harvested; analysis waits for an OpenAI key in Settings.'
  }
  if (searchQuery.value.trim()) return 'No insights match your search.'
  return 'No insights match these filters.'
})
</script>

<template>
  <div class="flex gap-6">
    <InsightFeedSidebar
      :threat-filter="threatFilter"
      :competitor-filter="competitorFilter"
      :category-filter="categoryFilter"
      :date-scope="dateScope"
      :selected-date="selectedDate"
      :custom-from="customFrom"
      :custom-to="customTo"
      :search-query="searchQuery"
      :sort-by="sortBy"
      :group-by="groupBy"
      :competitors="competitors"
      :unread-count="store.unreadInFeed.length"
      @update:threat-filter="threatFilter = $event"
      @update:competitor-filter="competitorFilter = $event"
      @update:category-filter="categoryFilter = $event"
      @update:date-scope="dateScope = $event"
      @update:selected-date="selectedDate = $event"
      @update:custom-from="customFrom = $event"
      @update:custom-to="customTo = $event"
      @update:search-query="searchQuery = $event"
      @update:sort-by="sortBy = $event"
      @update:group-by="groupBy = $event"
      @summary="summaryCompetitor = $event"
      @competitor-page="openCompetitorPage()"
      @today="setToday()"
      @yesterday="setYesterday()"
    />

    <div class="min-w-0 flex-1">
      <CategorySummaryStrip />
      <InsightAnalyticsPanel />

      <div class="mb-3 flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
        <input
          v-model="searchQuery"
          type="search"
          placeholder="Search…"
          class="w-full rounded-md border border-gray-700 bg-gray-900 px-3 py-2 text-sm text-gray-100 lg:hidden"
        />
        <p class="text-xs text-gray-500">{{ feedCountLabel }}</p>
        <button
          v-if="store.unreadInFeed.length"
          type="button"
          class="text-xs text-blue-400 hover:text-blue-300"
          title="Marks all items in the loaded feed as read (this browser only)"
          @click="store.markAllRead()"
        >
          Mark loaded as read
        </button>
      </div>

      <div v-if="activeFilterSummary.length" class="mb-3 flex flex-wrap gap-1.5 lg:hidden">
        <span
          v-for="(f, i) in activeFilterSummary"
          :key="i"
          class="rounded-full bg-gray-800 px-2 py-0.5 text-[10px] text-gray-300 ring-1 ring-gray-700"
        >
          {{ f }}
        </span>
      </div>

      <div class="mb-3 flex gap-2 overflow-x-auto pb-1 lg:hidden">
        <button
          v-for="f in ['all', 'high', 'starred', ...competitors.slice(0, 6)]"
          :key="f"
          class="shrink-0 rounded-full px-3 py-1 text-xs"
          :class="(f === 'all' && threatFilter === 'all') || (f === 'high' && threatFilter === 'high') || (f === 'starred' && threatFilter === 'starred') || competitorFilter === f ? 'bg-gray-700 text-white' : 'bg-gray-900 text-gray-400'"
          @click="f === 'all' ? (threatFilter = 'all', competitorFilter = 'all') : f === 'high' ? (threatFilter = 'high', competitorFilter = 'all') : f === 'starred' ? (threatFilter = 'starred') : (competitorFilter = f)"
        >
          {{ f === 'all' ? 'All' : f === 'high' ? 'High' : f === 'starred' ? '★' : f }}
        </button>
      </div>

      <InsightFeedList
        :grouped-by-day="groupedByDay"
        :group-by="groupBy"
        :filtered="filtered"
        :feed-loading="feedLoading"
        :feed-error="feedError"
        :empty-message="emptyFeedMessage"
        @retry="reloadFeed()"
        @load-more="reloadFeed(true)"
      />
    </div>

    <CompetitorSummaryPanel :competitor="summaryCompetitor" @close="summaryCompetitor = null" />
  </div>
</template>
