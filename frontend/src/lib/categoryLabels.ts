import type { InsightCategory } from '@/types/insight'

const LABELS: Record<InsightCategory, string> = {
  PRODUCT_LAUNCH: 'Product Launch',
  HIRING: 'Hiring',
  FINANCIAL_MOVE: 'Financial',
  PARTNERSHIP: 'Partnership',
  LEGAL: 'Legal',
  LEADERSHIP_CHANGE: 'Leadership',
  OTHER: 'Other',
}

export function categoryLabel(c: InsightCategory): string {
  return LABELS[c]
}

export const ALL_CATEGORIES: InsightCategory[] = [
  'PRODUCT_LAUNCH', 'HIRING', 'FINANCIAL_MOVE', 'PARTNERSHIP', 'LEGAL', 'LEADERSHIP_CHANGE', 'OTHER',
]
