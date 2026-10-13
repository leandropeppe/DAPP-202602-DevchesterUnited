import { expect, test } from '@playwright/test'

const player = {
  id: 'ply-1',
  name: 'Jugador Uno',
  league: 'LaLiga',
  team: 'Madrid',
  position: 'Forward',
  nationality: 'Argentina',
  jersey_number: null,
  statistics: {
    position: 'Forward',
    minutes_played: 1234,
    goals: 12,
    assists: 7,
    shots: 2.34,
    key_passes: 1.23,
    dribbles: 3.45,
    tackles: 4.56,
    rating: 7.89,
    yellow_cards: 2,
    red_cards: 1
  }
}

async function mockPlayers (page, detail = player) {
  await page.route('**/api/v1/players?*', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ data: {
        players: [{ ...player, statistics: undefined }],
        pagination: { current_page: 1, total_pages: 1, total_records: 1 }
      } })
    })
  })
  await page.route('**/api/v1/players/ply-1', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ data: detail })
    })
  })
}

test('navega del catalogo al detalle y vuelve', async ({ page }) => {
  await mockPlayers(page)
  await page.goto('/catalogo')

  await expect(page.getByRole('link', { name: 'Jugador Uno' })).toBeVisible()
  await expect(page.getByText('Argentina', { exact: true })).toBeVisible()
  await expect(page.getByText('Dorsal')).toHaveCount(0)
  await page.getByRole('link', { name: 'Jugador Uno' }).click()

  await expect(page).toHaveURL(/\/catalogo\/jugadores\/ply-1$/)
  await expect(page.getByRole('heading', { name: 'Jugador Uno' })).toBeVisible()
  await expect(page.getByText('Valoracion')).toBeVisible()
  await page.getByRole('link', { name: /Volver al catalogo/i }).click()
  await expect(page).toHaveURL(/\/catalogo$/)
})

test('muestra ausencia de estadisticas sin inventar valores', async ({ page }) => {
  await mockPlayers(page, { ...player, statistics: null })
  await page.goto('/catalogo/jugadores/ply-1')

  await expect(page.getByText(/no tiene estadisticas disponibles/i)).toBeVisible()
  await expect(page.getByRole('heading', { name: 'Jugador Uno' })).toBeVisible()
})

test('muestra error de jugador inexistente y recuperacion', async ({ page }) => {
  await page.route('**/api/v1/players/ply-missing', async (route) => {
    await route.fulfill({
      status: 404,
      contentType: 'application/json',
      body: JSON.stringify({ message: 'Jugador no encontrado' })
    })
  })
  await page.goto('/catalogo/jugadores/ply-missing')

  await expect(page.getByRole('alert')).toContainText('Jugador no encontrado')
  await page.getByRole('link', { name: /Volver al catalogo/i }).click()
  await expect(page).toHaveURL(/\/catalogo$/)
})
