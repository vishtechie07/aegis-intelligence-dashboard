/** Client-side guard for API excerpts that may still contain HTML from legacy rows. */
export function stripHtmlText(raw: string): string {
  return raw
    .replace(/<script[\s\S]*?<\/script>/gi, ' ')
    .replace(/<style[\s\S]*?<\/style>/gi, ' ')
    .replace(/<[^>]+>/g, ' ')
    .replace(/&nbsp;/gi, ' ')
    .replace(/&amp;/gi, '&')
    .replace(/&lt;/gi, '<')
    .replace(/&gt;/gi, '>')
    .replace(/&quot;/gi, '"')
    .replace(/&#39;/gi, "'")
    .replace(/\s+/g, ' ')
    .trim()
}

export function meaningfulExcerpt(
  excerpt: string | null | undefined,
  title: string,
  summary: string,
): string | null {
  if (!excerpt?.trim()) return null
  const clean = stripHtmlText(excerpt)
  if (!clean) return null
  const t = title.trim()
  const s = summary.trim()
  if (clean === t || clean === s) return null
  if (clean.startsWith(t)) {
    const rest = clean.slice(t.length).trim()
    if (!rest || rest.length < 8 || /^https?:\/\//i.test(rest) || rest.includes('news.google.com/rss/articles')) {
      return null
    }
  }
  if (/^https?:\/\//i.test(clean) || clean.includes('news.google.com/rss/articles')) return null
  return clean
}
