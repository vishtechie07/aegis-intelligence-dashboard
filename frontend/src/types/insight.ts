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
  /** UI-only: true for 3 seconds after arrival to trigger pulse animation */
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

/** Mirrors DeepDiveHistoryEntry.java */
export interface DeepDiveHistoryEntry {
  id: number
  newsId: number
  question: string
  analysis: string
  createdAt: string
  sources: DeepDiveSource[]
  ragUsed: boolean
}
