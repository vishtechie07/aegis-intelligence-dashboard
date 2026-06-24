import { chromium } from '@playwright/test'
import { mkdir } from 'node:fs/promises'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', '..')
const outDir = path.join(root, 'docs', 'screenshots')
const baseUrl = process.env.SCREENSHOT_BASE_URL ?? 'http://localhost:3000'
const apiBase = process.env.SCREENSHOT_API_BASE_URL ?? 'http://localhost:8080'
const competitorName = process.env.SCREENSHOT_COMPETITOR ?? 'OpenAI'

await mkdir(outDir, { recursive: true })

async function waitForFeed(page) {
  await page.waitForSelector('.border-l-4', { timeout: 60_000 })
  await page.getByText(/in feed/i).first().waitFor({ timeout: 30_000 }).catch(() => {})
  await page.waitForTimeout(1500)
}

const browser = await chromium.launch()
const page = await browser.newPage({ viewport: { width: 1440, height: 900 } })

// Dashboard — sidebar + feed + stats visible at 1440px
await page.goto(baseUrl, { waitUntil: 'domcontentloaded', timeout: 60_000 })
await waitForFeed(page)
await page.screenshot({ path: path.join(outDir, 'dashboard.png') })

// Competitor drill-down page
await page.goto(`${baseUrl}/competitor/${encodeURIComponent(competitorName)}`, {
  waitUntil: 'domcontentloaded',
  timeout: 60_000,
})
await page.getByRole('heading', { name: competitorName }).waitFor({ timeout: 30_000 })
await waitForFeed(page)
await page.screenshot({ path: path.join(outDir, 'competitor.png') })

// Ask Agent card panel
await page.goto(baseUrl, { waitUntil: 'domcontentloaded', timeout: 60_000 })
await waitForFeed(page)

const card = page.locator('.border-l-4.bg-gray-900').first()
await card.scrollIntoViewIfNeeded()
await card.getByRole('button', { name: 'Ask Agent' }).click()
await card.locator('input[type="text"]').waitFor({ state: 'visible', timeout: 15_000 }).catch(() => {})
await page.waitForTimeout(1500)
await card.screenshot({ path: path.join(outDir, 'ask-agent.png') })

if ((await card.getByText(/Sources used/).count()) > 0) {
  await card.screenshot({ path: path.join(outDir, 'ask-agent-sources.png') })
}

await browser.close()
console.log(`Screenshots saved to ${outDir}`)
