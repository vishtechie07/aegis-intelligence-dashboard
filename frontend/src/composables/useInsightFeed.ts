import type {
  CompetitorInsightSummary,
  DateScope,
  FeedSort,
  GroupBy,
  InsightCategory,
  InsightFeedPage,
  RelatedInsightBrief,
} from '@/types/insight'

const API_BASE = import.meta.env.VITE_API_BASE_URL ?? ''

function apiUrl(path: string): string {
  const base = API_BASE.replace(/\/$/, '')
  return `${base}${path.startsWith('/') ? path : `/${path}`}`
}

export interface FeedQuery {
  competitor?: string
  category?: InsightCategory | 'all'
  minThreat?: number
  search?: string
  dateFrom?: string
  dateTo?: string
  sort?: FeedSort
  offset?: number
  limit?: number
  ids?: string
}

function buildFeedParams(q: FeedQuery): string {
  const p = new URLSearchParams()
  if (q.competitor && q.competitor !== 'all') p.set('competitor', q.competitor)
  if (q.category && q.category !== 'all') p.set('category', q.category)
  if (q.minThreat != null) p.set('minThreat', String(q.minThreat))
  if (q.search?.trim()) p.set('search', q.search.trim())
  if (q.dateFrom) p.set('dateFrom', q.dateFrom)
  if (q.dateTo) p.set('dateTo', q.dateTo)
  if (q.sort) p.set('sort', q.sort)
  if (q.ids) p.set('ids', q.ids)
  p.set('offset', String(q.offset ?? 0))
  p.set('limit', String(q.limit ?? 50))
  return p.toString()
}

export function dateRangeForScope(
  scope: DateScope,
  day?: string,
  customFrom?: string,
  customTo?: string,
): { dateFrom?: string; dateTo?: string } {
  const now = new Date()
  if (scope === 'all') return {}
  if (scope === 'day' && day) {
    const start = new Date(`${day}T00:00:00`)
    const end = new Date(start)
    end.setDate(end.getDate() + 1)
    return { dateFrom: start.toISOString(), dateTo: end.toISOString() }
  }
  if (scope === '7d') {
    const start = new Date(now)
    start.setDate(start.getDate() - 7)
    return { dateFrom: start.toISOString() }
  }
  if (scope === '30d') {
    const start = new Date(now)
    start.setDate(start.getDate() - 30)
    return { dateFrom: start.toISOString() }
  }
  if (scope === 'custom') {
    const out: { dateFrom?: string; dateTo?: string } = {}
    if (customFrom) out.dateFrom = new Date(`${customFrom}T00:00:00`).toISOString()
    if (customTo) {
      const end = new Date(`${customTo}T00:00:00`)
      end.setDate(end.getDate() + 1)
      out.dateTo = end.toISOString()
    }
    return out
  }
  return {}
}

export async function fetchFeed(query: FeedQuery): Promise<InsightFeedPage> {
  const res = await fetch(`${apiUrl('/api/insights/feed')}?${buildFeedParams(query)}`)
  if (!res.ok) throw new Error(`feed ${res.status}`)
  return res.json()
}

export async function fetchStats() {
  const res = await fetch(apiUrl('/api/insights/stats'))
  if (!res.ok) throw new Error(`stats ${res.status}`)
  return res.json()
}

export async function fetchAnalytics(days = 7) {
  const res = await fetch(`${apiUrl('/api/insights/analytics')}?days=${days}`)
  if (!res.ok) throw new Error(`analytics ${res.status}`)
  return res.json()
}

export async function fetchCompetitorSummary(name: string): Promise<CompetitorInsightSummary> {
  const res = await fetch(apiUrl(`/api/insights/competitor/${encodeURIComponent(name)}/summary`))
  if (!res.ok) throw new Error(`summary ${res.status}`)
  return res.json()
}

export async function fetchRelated(newsId: number, limit = 3): Promise<RelatedInsightBrief[]> {
  const res = await fetch(`${apiUrl(`/api/insights/${newsId}/related`)}?limit=${limit}`)
  if (!res.ok) return []
  return res.json()
}

export function insightSortIso(
  i: { publishedAt: string | null; processedAt: string },
  groupBy: GroupBy,
): string {
  if (groupBy === 'processed') return i.processedAt || i.publishedAt || ''
  return i.publishedAt || i.processedAt || ''
}
