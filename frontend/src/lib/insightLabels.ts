import type { SourceType } from '@/types/insight'

const SOURCE_LABELS: Record<SourceType, string> = {
  RSS: 'RSS',
  GDELT: 'GDELT',
  REDDIT: 'Reddit',
  HACKERNEWS: 'Hacker News',
  EDGAR: 'SEC EDGAR',
  GITHUB: 'GitHub',
  GOOGLENEWS: 'Google News',
  FINANCE: 'Yahoo Finance',
  CONTRACT: 'USASpending',
  MACRO: 'Macro / World Bank',
}

const SOURCE_ICONS: Record<SourceType, string> = {
  RSS: '📰',
  GDELT: '🌍',
  REDDIT: '🤖',
  HACKERNEWS: '🔶',
  EDGAR: '📋',
  GITHUB: '🐙',
  GOOGLENEWS: '🔍',
  FINANCE: '📈',
  CONTRACT: '🏛️',
  MACRO: '🏦',
}

export function sourceLabel(type: SourceType | null | undefined): string {
  if (!type) return 'Unknown source'
  return SOURCE_LABELS[type] ?? type
}

export function sourceIcon(type: SourceType | null | undefined): string {
  if (!type) return '📡'
  return SOURCE_ICONS[type] ?? '📡'
}

/** Aligns with API high-threat filter (≥7) and Strategist prompt tiers. */
export function threatLabel(level: number): string {
  if (level >= 9) return 'Critical'
  if (level >= 7) return 'High'
  if (level >= 5) return 'Elevated'
  return 'Low'
}

export const THREAT_TOOLTIP =
  'AI-scored 1–10 for strategic impact. 9–10 Critical, 7–8 High (matches “High threat” filter), 5–6 Elevated, 1–4 Low. LEGAL/EDGAR items have minimum floors.'

export const UNREAD_FILTER_TOOLTIP =
  'Unread items in the currently loaded feed only. Read/star state is stored in this browser — not synced across devices.'

export const STARRED_FILTER_TOOLTIP =
  'Starred IDs saved in this browser; the full list is fetched from the server when you filter.'
