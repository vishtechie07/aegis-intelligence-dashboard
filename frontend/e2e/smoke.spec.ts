import { expect, test } from '@playwright/test'

test.describe('Dashboard', () => {
  test('shell renders with feed controls', async ({ page }) => {
    await page.goto('/')
    await expect(page.getByText('AEGIS')).toBeVisible()
    await expect(page.getByText('Competitor Intelligence Engine')).toBeVisible()
    await expect(page.getByPlaceholder('Search…')).toBeVisible()
  })

  test('competitor route resolves via SPA fallback', async ({ page }) => {
    await page.goto('/competitor/OpenAI')
    await expect(page.getByRole('heading', { name: 'OpenAI' })).toBeVisible()
    await expect(page.getByText('Competitor intelligence')).toBeVisible()
    await expect(page.getByRole('button', { name: '← Dashboard' })).toBeVisible()
  })
})
