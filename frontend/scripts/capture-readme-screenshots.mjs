import { chromium } from '@playwright/test'
import { mkdir } from 'node:fs/promises'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', '..')
const outDir = path.join(root, 'docs', 'screenshots')
const baseUrl = process.env.SCREENSHOT_BASE_URL ?? 'http://localhost:3000'
const apiBase = process.env.SCREENSHOT_API_BASE_URL ?? 'http://localhost:8080'

await mkdir(outDir, { recursive: true })

async function findHistoryArticle(page) {
  const latestRes = await fetch(`${apiBase}/api/insights/latest?limit=50`)
  if (!latestRes.ok) return null
  const latest = await latestRes.json()
  let best = null
  for (const row of latest) {
    const hres = await fetch(`${apiBase}/api/insights/deep-dive/history?newsId=${row.newsId}`)
    if (!hres.ok) continue
    const hist = await hres.json()
    if (!Array.isArray(hist) || hist.length === 0) continue
    const snippet = row.title?.slice(0, 30) ?? ''
    if (snippet && (await page.getByText(snippet, { exact: false }).count()) > 0) {
      return row
    }
    if (!best || hist.length > best.historyCount) {
      best = { ...row, historyCount: hist.length }
    }
  }
  return best
}

const browser = await chromium.launch()
const page = await browser.newPage({ viewport: { width: 1440, height: 900 } })

await page.goto(baseUrl, { waitUntil: 'domcontentloaded', timeout: 60_000 })
await page.waitForSelector('.border-l-4', { timeout: 30_000 })
await page.waitForTimeout(2000)
await page.screenshot({ path: path.join(outDir, 'dashboard.png') })

const article = await findHistoryArticle(page)
const card = article
  ? page.locator('.border-l-4').filter({
      hasText: article.title?.slice(0, 35) ?? article.competitorName,
    }).first()
  : page.locator('.border-l-4').first()

await card.scrollIntoViewIfNeeded()
await card.getByRole('button', { name: /Ask Agent|Close/ }).click()
await page.waitForTimeout(2500)
await card.screenshot({ path: path.join(outDir, 'ask-agent.png') })

if ((await card.getByText(/Sources used/).count()) > 0) {
  await card.screenshot({ path: path.join(outDir, 'ask-agent-sources.png') })
}

await browser.close()
console.log(`Screenshots saved to ${outDir}`)
