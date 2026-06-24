import { computed, type Ref } from 'vue'
import type { GroupBy, Insight } from '@/types/insight'
import { insightSortIso } from '@/composables/useInsightFeed'

export type FeedUnit =
  | { type: 'single'; insight: Insight }
  | { type: 'cluster'; key: string; items: Insight[] }

export type DayGroup = { key: string; heading: string; units: FeedUnit[] }

function dayKeyLocal(iso: string): string {
  if (!iso) return 'unknown'
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return 'unknown'
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

function formatDayHeading(ymd: string): string {
  if (ymd === 'unknown') return 'Unknown date'
  const [y, m, day] = ymd.split('-').map(Number)
  return new Date(y, m - 1, day).toLocaleDateString(undefined, {
    weekday: 'short', month: 'short', day: 'numeric', year: 'numeric',
  })
}

export function buildFeedUnits(items: Insight[]): FeedUnit[] {
  const byCluster = new Map<string, Insight[]>()
  for (const i of items) {
    if (i.clusterKey) {
      const list = byCluster.get(i.clusterKey) ?? []
      list.push(i)
      byCluster.set(i.clusterKey, list)
    }
  }
  const seen = new Set<string>()
  const units: FeedUnit[] = []
  for (const i of items) {
    if (i.clusterKey && (byCluster.get(i.clusterKey)?.length ?? 0) > 1) {
      if (!seen.has(i.clusterKey)) {
        seen.add(i.clusterKey)
        units.push({ type: 'cluster', key: i.clusterKey, items: byCluster.get(i.clusterKey)! })
      }
    } else {
      units.push({ type: 'single', insight: i })
    }
  }
  return units
}

export function useFeedGrouping(
  filtered: Ref<Insight[]>,
  groupBy: Ref<GroupBy>,
) {
  const groupedByDay = computed((): DayGroup[] => {
    const map = new Map<string, Insight[]>()
    for (const i of filtered.value) {
      const k = dayKeyLocal(insightSortIso(i, groupBy.value))
      if (!map.has(k)) map.set(k, [])
      map.get(k)!.push(i)
    }
    const keys = [...map.keys()].sort((a, b) => b.localeCompare(a))
    return keys.map(k => ({
      key: k,
      heading: formatDayHeading(k),
      units: buildFeedUnits(map.get(k)!),
    }))
  })

  return { groupedByDay }
}
