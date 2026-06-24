import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  dateRangeForScope,
  fetchFeed,
} from '@/composables/useInsightFeed'
import { useInsightStore } from '@/stores/insightStore'
import type { DateScope, FeedSort, GroupBy, InsightCategory } from '@/types/insight'
import { ALL_CATEGORIES, categoryLabel } from '@/lib/categoryLabels'

export type ThreatFilter = 'all' | 'high' | 'starred' | 'unread'

const PAGE = 50

function qStr(v: unknown): string | undefined {
  if (typeof v === 'string') return v
  if (Array.isArray(v) && typeof v[0] === 'string') return v[0]
  return undefined
}

export function todayLocal(): string {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

export function useFeedFilters() {
  const store = useInsightStore()
  const route = useRoute()
  const router = useRouter()

  const feedLoading = ref(false)
  const feedError = ref(false)
  const summaryCompetitor = ref<string | null>(null)

  const threatFilter = ref<ThreatFilter>('all')
  const competitorFilter = ref<string>('all')
  const categoryFilter = ref<InsightCategory | 'all'>('all')
  const dateScope = ref<DateScope>('all')
  const selectedDate = ref(todayLocal())
  const customFrom = ref('')
  const customTo = ref('')
  const searchQuery = ref('')
  const sortBy = ref<FeedSort>('processed')
  const groupBy = ref<GroupBy>('published')

  let syncingFromRoute = false
  let searchDebounce: ReturnType<typeof setTimeout> | null = null

  function buildQuery(): Record<string, string> {
    const out: Record<string, string> = {}
    if (threatFilter.value === 'high') out.threat = 'high'
    if (threatFilter.value === 'starred') out.starred = '1'
    if (threatFilter.value === 'unread') out.unread = '1'
    if (competitorFilter.value !== 'all') out.competitor = competitorFilter.value
    if (categoryFilter.value !== 'all') out.category = categoryFilter.value
    if (dateScope.value !== 'all') out.date = dateScope.value
    if (dateScope.value === 'day') out.day = selectedDate.value
    if (searchQuery.value.trim()) out.q = searchQuery.value.trim()
    if (sortBy.value !== 'processed') out.sort = sortBy.value
    if (dateScope.value === 'custom') {
      if (customFrom.value) out.from = customFrom.value
      if (customTo.value) out.to = customTo.value
    }
    if (groupBy.value !== 'published') out.group = groupBy.value
    return out
  }

  function applyRouteQuery() {
    syncingFromRoute = true
    const q = route.query
    const tf = qStr(q.threat)
    threatFilter.value = tf === 'high' ? 'high' : qStr(q.starred) === '1' ? 'starred' : qStr(q.unread) === '1' ? 'unread' : 'all'
    competitorFilter.value = qStr(q.competitor) || 'all'
    const cat = qStr(q.category)
    categoryFilter.value = cat && (ALL_CATEGORIES as readonly string[]).includes(cat) ? (cat as InsightCategory) : 'all'
    const ds = qStr(q.date)
    if (ds === 'day' || ds === '7d' || ds === '30d' || ds === 'custom') dateScope.value = ds
    else dateScope.value = 'all'
    const dy = qStr(q.day)
    if (dy && /^\d{4}-\d{2}-\d{2}$/.test(dy)) selectedDate.value = dy
    const cf = qStr(q.from)
    const ct = qStr(q.to)
    if (cf) customFrom.value = cf
    if (ct) customTo.value = ct
    searchQuery.value = qStr(q.q) ?? ''
    const s = qStr(q.sort)
    sortBy.value = s === 'published' || s === 'threat' ? s : 'processed'
    groupBy.value = qStr(q.group) === 'processed' ? 'processed' : 'published'
    void nextTick(() => { syncingFromRoute = false })
  }

  async function reloadFeed(append = false) {
    if (threatFilter.value === 'starred') {
      const ids = store.starredIds()
      if (!ids.length) {
        store.setFeedPage([], 0, false, false)
        return
      }
      feedLoading.value = true
      try {
        const page = await fetchFeed({ ids: ids.join(','), limit: 200 })
        store.setFeedPage(page.items, page.items.length, false, false)
      } catch {
        feedError.value = true
        store.setFeedPage([], 0, false, false)
      } finally {
        feedLoading.value = false
      }
      return
    }
    feedLoading.value = !append
    feedError.value = false
    const offset = append ? store.insights.length : 0
    const { dateFrom, dateTo } = dateRangeForScope(dateScope.value, selectedDate.value, customFrom.value, customTo.value)
    try {
      const page = await fetchFeed({
        competitor: competitorFilter.value,
        category: categoryFilter.value,
        minThreat: threatFilter.value === 'high' ? 7 : undefined,
        search: searchQuery.value,
        dateFrom,
        dateTo,
        sort: sortBy.value,
        offset,
        limit: PAGE,
      })
      store.setFeedPage(page.items, page.total, page.hasMore, append)
    } catch {
      feedError.value = true
      if (!append) store.setFeedPage([], 0, false, false)
    } finally {
      feedLoading.value = false
    }
  }

  const filtered = computed(() => {
    let list = store.visibleInsights
    if (threatFilter.value === 'unread') list = store.unreadInFeed
    if (categoryFilter.value !== 'all') list = list.filter(i => i.category === categoryFilter.value)
    if (searchQuery.value.trim()) {
      const q = searchQuery.value.toLowerCase()
      list = list.filter(i => i.title.toLowerCase().includes(q) || i.summary.toLowerCase().includes(q))
    }
    return list
  })

  const feedCountLabel = computed(() => {
    const loaded = store.visibleInsights.length
    const total = store.feedTotal
    return total > loaded ? `${loaded} of ${total.toLocaleString()} in feed` : `${total.toLocaleString()} in feed`
  })

  const activeFilterSummary = computed(() => {
    const parts: string[] = []
    if (threatFilter.value === 'high') parts.push('High threat ≥7')
    if (threatFilter.value === 'starred') parts.push('Starred')
    if (threatFilter.value === 'unread') parts.push('Unread in feed')
    if (competitorFilter.value !== 'all') parts.push(competitorFilter.value)
    if (categoryFilter.value !== 'all') parts.push(categoryLabel(categoryFilter.value))
    if (dateScope.value !== 'all') parts.push(`Date: ${dateScope.value}`)
    if (searchQuery.value.trim()) parts.push(`"${searchQuery.value.trim()}"`)
    return parts
  })

  function openCompetitorPage() {
    if (competitorFilter.value !== 'all') {
      void router.push({ path: `/competitor/${encodeURIComponent(competitorFilter.value)}` })
    }
  }

  function setToday() { selectedDate.value = todayLocal() }
  function setYesterday() {
    const d = new Date()
    d.setDate(d.getDate() - 1)
    selectedDate.value = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
  }

  onMounted(() => {
    applyRouteQuery()
    if (!store.insights.length) void reloadFeed()
  })

  watch(() => route.query, () => applyRouteQuery(), { deep: true })

  watch(
    [threatFilter, competitorFilter, categoryFilter, dateScope, selectedDate, sortBy, customFrom, customTo],
    () => {
      if (syncingFromRoute) return
      void router.replace({ path: route.path, query: buildQuery() })
      void reloadFeed()
    },
  )

  watch(searchQuery, () => {
    if (syncingFromRoute) return
    if (searchDebounce) clearTimeout(searchDebounce)
    searchDebounce = setTimeout(() => {
      void router.replace({ path: route.path, query: buildQuery() })
      void reloadFeed()
    }, 350)
  })

  watch(dateScope, scope => {
    if (scope === 'day' && !selectedDate.value) selectedDate.value = todayLocal()
  })

  return {
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
  }
}
