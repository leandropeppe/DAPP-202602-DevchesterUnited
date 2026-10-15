import { expect, test } from '@playwright/test'

const player = {
  id: 'ply-1',
  name: 'Jugador Uno',
  league: 'LaLiga',
  team: 'Madrid',
  position: 'Forward',
  nationality: 'Argentina',
  statistics: {
    generales: { minsPlayed: 1234, rating: 7.89 },
    ofensivas: { goal: 12, assistTotal: 7, shotsPerGame: 2.34 },
    defensivas: { tacklePerGame: 4.56, interceptionPerGame: 1.2 },
    pases: { totalPassesPerGame: 45.7, passSuccess: 86.44 }
  }
}

test('recorre detalle y las cuatro pestañas de estadísticas', async ({ page }) => {
  await page.route('**/api/v1/players?*', route => route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify({ data: { players: [{ ...player, statistics: undefined }], pagination: { current_page: 1, total_pages: 1, total_records: 1 } } })
  }))
  await page.route('**/api/v1/players/ply-1', route => route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify({ data: player })
  }))

  await page.goto('/catalogo')
  await page.getByRole('link', { name: 'Jugador Uno' }).click()
  await expect(page.getByRole('tab', { name: 'Resumen' })).toHaveAttribute('aria-selected', 'true')
  await expect(page.locator('.player-statistics').getByText(/1\.234|1234/)).toBeVisible()

  await page.getByRole('tab', { name: 'Ataque' }).click()
  await expect(page.locator('.player-statistics').getByText('2,34')).toBeVisible()
  await page.getByRole('tab', { name: 'Defensa' }).click()
  await expect(page.locator('.player-statistics').getByText(/4,56|4\.56/)).toBeVisible()
  await page.getByRole('tab', { name: 'Pases' }).click()
  await expect(page.locator('.player-statistics').getByText(/86,44|86\.44/)).toBeVisible()

  await page.getByRole('link', { name: /Volver al catálogo/i }).click()
  await expect(page).toHaveURL(/\/catalogo$/)
})
