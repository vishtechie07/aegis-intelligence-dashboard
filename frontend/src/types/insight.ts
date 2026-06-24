/** Mirrors InsightEvent.java record exactly — type-safe full-stack handoff */
export interface Insight {
  id: number
  newsId: number
  competitorName: string
  title: string
  sourceUrl: string | null
  sourceType: SourceType | null
  agentName: string
  category: InsightCategory | null
  threatLevel: number
  summary: string
  strategicAdvice: string
  publishedAt: string | null
  processedAt: string
  contentExcerpt: string | null
  ragAvailable: boolean
  clusterKey: string | null
  /** UI-only */
  isNew?: boolean
}

export type SourceType =
  | 'RSS'
  | 'GDELT'
  | 'REDDIT'
  | 'HACKERNEWS'
  | 'EDGAR'
  | 'GITHUB'
  | 'GOOGLENEWS'
  | 'FINANCE'
  | 'CONTRACT'
  | 'MACRO'

export type InsightCategory =
  | 'PRODUCT_LAUNCH'
  | 'HIRING'
  | 'FINANCIAL_MOVE'
  | 'PARTNERSHIP'
  | 'LEGAL'
  | 'LEADERSHIP_CHANGE'
  | 'OTHER'

export interface InsightFeedPage {
  items: Insight[]
  total: number
  hasMore: boolean
}

export interface InsightStats {
  totalArticles: number
  totalInsights: number
  filteredArticles: number
  todayHarvested: number
  todayAnalyzed: number
  todayFiltered: number
  highThreatCount: number
}

export interface CategoryCount {
  category: string
  count: number
}

export interface SourceCount {
  sourceType: string
  count: number
}

export interface ThreatHeatmapCell {
  competitorName: string
  count: number
}

export interface InsightAnalytics {
  categoriesLast7Days: CategoryCount[]
  sourcesLast7Days: SourceCount[]
  highThreatByCompetitor: ThreatHeatmapCell[]
}

export interface CompetitorInsightSummary {
  competitorName: string
  totalInsights: number
  highThreatCount: number
  byCategory: CategoryCount[]
  bySource: SourceCount[]
  recentHighThreat: Insight[]
}

export interface RelatedInsightBrief {
  newsId: number
  insightId: number | null
  title: string
  sourceUrl: string | null
  threatLevel: number | null
  competitorName: string
}

export type FeedSort = 'processed' | 'published' | 'threat'
export type DateScope = 'all' | 'day' | '7d' | '30d' | 'custom'
export type GroupBy = 'published' | 'processed'

export interface DeepDiveRequest {
  newsId: number
  question: string
}

export interface DeepDiveSource {
  newsId: number
  title: string
  excerpt: string
  sourceUrl: string | null
  currentArticle: boolean
}

export interface DeepDiveResponse {
  analysis: string
  sources: DeepDiveSource[]
  ragUsed: boolean
}

export interface DeepDiveHistoryEntry {
  id: number
  newsId: number
  question: string
  analysis: string
  createdAt: string
  sources: DeepDiveSource[]
  ragUsed: boolean
}

export interface InsightUiState {
  read: number[]
  starred: number[]
  dismissed: number[]
}
