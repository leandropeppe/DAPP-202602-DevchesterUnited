import { expect, test } from '@playwright/test'

const player = {
  id: 'ply-1', name: 'Jugador Uno', league: 'LaLiga', team: 'Madrid', position: 'Forward', nationality: 'Argentina',
  statistics: { generales: { minsPlayed: 900, rating: 7.8 }, ofensivas: { goal: 11, assistTotal: 5 } }
}

const config = {
  playerId: 'ply-1', positions: ['Forward'], resolvedPosition: 'Forward', strategyId: 'POSITIONAL', strategyVersion: 2,
  provisionalStrategy: false, currentQuote: 100, horizons: [3, 5, 8, 10], currentApps: 10, currentMinutes: 900,
  blockingReasons: [], statistics: [{ metric: 'GOALS', label: 'Goles', kind: 'ACCUMULATIVE', currentValue: 11, editable: true, min: 0, max: null }]
}

async function mockSimulator (page) {
  await page.route('**/api/v1/players/ply-1', route => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ data: player }) }))
  await page.route('**/api/v1/players/ply-1/valuation-simulator', async route => {
    if (route.request().method() === 'GET') return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ data: config }) })
    return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ data: { currentQuote: 100, simulatedQuote: 110, monetaryDifference: 10, percentageDifference: 10, projectedStatistics: { GOALS: 13 } } }) })
  })
}

test('abre el simulador, recalcula cinco veces y restablece', async ({ page }) => {
  await mockSimulator(page)
  await page.goto('/catalogo/jugadores/ply-1')
  await expect(page.getByRole('heading', { name: 'Jugador Uno', level: 1 })).toBeVisible()
  await page.getByRole('button', { name: 'Simular cotización' }).click()
  await expect(page.getByRole('dialog')).toBeVisible()
  for (let index = 0; index < 5; index += 1) {
    await page
        .getByRole('button', {
          name: 'Simular',
          exact: true
        })
        .click()

    await expect(
        page.getByRole('heading', {
          name: 'Cotización proyectada'
        })
    ).toBeVisible()

    if (index < 4) {
      await page
          .getByRole('button', {
            name: 'Modificar escenario'
          })
          .click()

      await expect(
          page.getByRole('button', {
            name: 'Simular',
            exact: true
          })
      ).toBeVisible()
    }
  }
  await page
      .getByRole('button', {
        name: 'Restablecer'
      })
      .click()

  await expect(
      page.getByLabel('Goles predicción')
  ).toHaveValue('0')
  await page.getByRole('button', { name: 'Cerrar simulador' }).click()
  await expect(page.getByRole('heading', { name: 'Jugador Uno', level: 1 })).toBeVisible()
})
