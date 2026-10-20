import { expect, test } from '@playwright/test'

const currentPlayer = {
  id: 'ply_11111111-1111-4111-8111-111111111111',
  name: 'Jugador vigente',
  league: 'Liga',
  team: 'Equipo',
  player_active: true,
  team_active: true,
  tokens_available: 25,
  operable: true,
  quote_id: '22222222-2222-4222-8222-222222222222',
  quote_version: 7,
  price_per_token: 12.5,
  quote_status: 'VALID',
  quote_availability: 'CURRENT'
}

function jwtFor (userId) {
  const payload = Buffer.from(JSON.stringify({ sub: userId, exp: Math.floor(Date.now() / 1000) + 3600 })).toString('base64url')
  return `${userId}.${payload}.signature`
}

async function authenticate (page, userId) {
  await page.addInitScript((jwt) => sessionStorage.setItem('devchester.jwt', jwt), jwtFor(userId))
}

function success (data) {
  return {
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify({ status: 'success', data })
  }
}

async function mockMarketApi (context, { pendingPurchase = false } = {}) {
  let purchaseStatus = pendingPurchase ? 'PROCESSING' : 'CONFIRMED'
  await context.route('**/api/v1/market/**', async (route) => {
    const request = route.request()
    const url = new URL(request.url())
    const authorization = request.headers().authorization || ''
    const userKey = authorization.includes('user-b') ? 'B' : 'A'

    if (url.pathname.endsWith('/balance')) {
      await route.fulfill(success({ balance: userKey === 'A' ? 1500 : 750 }))
      return
    }

    if (url.pathname.endsWith('/players')) {
      await route.fulfill(success({
        players: [currentPlayer, {
          ...currentPlayer,
          id: 'ply_33333333-3333-4333-8333-333333333333',
          name: 'Jugador histórico',
          quote_status: 'REPLACED',
          quote_availability: 'HISTORICAL',
          operable: false
        }, {
          ...currentPlayer,
          id: 'ply_44444444-4444-4444-8444-444444444444',
          name: 'Jugador sin cotización',
          quote_id: null,
          quote_version: null,
          price_per_token: null,
          quote_status: 'ABSENT',
          quote_availability: 'ABSENT',
          operable: false
        }],
        pagination: { current_page: Number(url.searchParams.get('page') || 1), total_pages: 1, total_records: 3 }
      }))
      return
    }

    if (url.pathname.endsWith('/purchases') && request.method() === 'POST') {
      await route.fulfill(success({ status: purchaseStatus, updated_at: '2026-10-19T12:00:00Z', total_amount: 12.5, transaction_id: purchaseStatus === 'CONFIRMED' ? 'tx-purchase-1' : null }))
      return
    }

    if (url.pathname.endsWith('/sales') && request.method() === 'POST') {
      await route.fulfill(success({ status: 'REJECTED', updated_at: '2026-10-19T12:00:00Z', total_amount: null }))
      return
    }

    if (url.pathname.includes('/operations/')) {
      purchaseStatus = 'CONFIRMED'
      await route.fulfill(success({ idempotency_key: decodeURIComponent(url.pathname.split('/').pop()), operation_type: 'PURCHASE', status: purchaseStatus, updated_at: '2026-10-19T12:01:00Z' }))
      return
    }

    if (url.pathname.endsWith('/portfolio')) {
      await route.fulfill(success({
        open_positions: [{
          player_id: '11111111-1111-4111-8111-111111111111',
          quantity: 2,
          pending_acquisition_cost: 20,
          weighted_average_cost: 10,
          quote_availability: 'CURRENT',
          unit_price: 12.5,
          current_value: 25,
          unrealized_pnl: 5
        }],
        realized_pnl: userKey === 'A' ? 4.5 : 1.5,
        unrealized_pnl: 5,
        combined_result: 9.5,
        combined_result_complete: true
      }))
      return
    }

    if (url.pathname.endsWith('/transactions')) {
      const currentPage = Number(url.searchParams.get('page') || 1)
      await route.fulfill(success({
        transactions: [{
          transaction_id: `tx-${userKey}-${currentPage}`,
          confirmed_at: '2026-10-19T12:00:00Z',
          player_id: '11111111-1111-4111-8111-111111111111',
          player_name_snapshot: userKey === 'A' ? 'Jugador histórico A' : 'Jugador histórico B',
          operation_type: 'SALE',
          quantity: 1,
          unit_price: 12.5,
          total_amount: 12.5,
          quote_version: 7,
          realized_pnl: userKey === 'A' ? 2.5 : 0.5
        }],
        pagination: { current_page: currentPage, page_size: 20, total_pages: 2, total_records: 2 }
      }))
      return
    }

    await route.fulfill({ status: 404, contentType: 'application/json', body: JSON.stringify({ message: 'Ruta de mercado no simulada' }) })
  })
}

test('muestra mercado, cotizaciones, compra confirmada y rechazo de venta', async ({ page, context }) => {
  await authenticate(page, 'usr_user-a')
  await mockMarketApi(context)
  await page.goto('/market')

  await expect(page.getByRole('heading', { name: 'Mercado de jugadores' })).toBeVisible()
  await expect(page.getByLabel('Saldo disponible')).toContainText('1.500,00')
  await expect(page.getByText('Cotización histórica.')).toBeVisible()
  await expect(page.getByText('Cotización no disponible.')).toBeVisible()

  const currentCard = page.locator('article.market-player-card').filter({ hasText: 'Jugador vigente' })
  await currentCard.getByRole('button', { name: 'Comprar' }).click()
  await expect(page.getByText('Versión aceptada')).toBeVisible()
  await expect(page.getByText('Total estimado')).toBeVisible()
  await page.getByRole('checkbox').check()
  await page.getByRole('button', { name: /confirmar compra/i }).click()
  await expect(page.locator('.market-operation-result').getByRole('status')).toContainText('fue confirmada')

  await page.getByRole('button', { name: 'Cerrar', exact: true }).click()
  await currentCard.getByRole('button', { name: 'Vender' }).click()
  await page.getByRole('checkbox').check()
  await page.getByRole('button', { name: /confirmar venta/i }).click()
  await expect(page.locator('.market-operation-result').getByRole('alert')).toContainText('fue rechazada')
})

test('recupera una operación pendiente y consulta portfolio e historial', async ({ page, context }) => {
  await authenticate(page, 'usr_user-a')
  await mockMarketApi(context, { pendingPurchase: true })
  await page.goto('/market')

  const currentCard = page.locator('article.market-player-card').filter({ hasText: 'Jugador vigente' })
  await currentCard.getByRole('button', { name: 'Comprar' }).click()
  await page.getByRole('checkbox').check()
  await page.getByRole('button', { name: /confirmar compra/i }).click()
  await expect(page.locator('.market-operation-result').getByRole('status')).toContainText('pendiente')
  await page.getByRole('button', { name: 'Consultar estado' }).click()
  await expect(page.locator('.market-operation-result').getByRole('status')).toContainText('fue confirmada')

  await page.getByRole('link', { name: 'Portfolio', exact: true }).click()
  await expect(page).toHaveURL(/\/market\/portfolio$/)
  await expect(page.getByRole('heading', { name: 'Mi portfolio' })).toBeVisible()
  await expect(page.getByText('P/L realizada')).toBeVisible()

  await page.getByRole('link', { name: 'Historial' }).click()
  await expect(page).toHaveURL(/\/market\/transactions$/)
  await expect(page.getByText('Jugador histórico A')).toBeVisible()
  await page.getByRole('combobox', { name: 'Tipo' }).selectOption('SALE')
  await page.getByRole('button', { name: 'Aplicar filtros' }).click()
  await expect(page.getByText('Página 1 de 2')).toBeVisible()
  await page.getByRole('button', { name: 'Siguiente' }).click()
  await expect(page.getByText('Página 2 de 2')).toBeVisible()
})

test('aísla saldo y resultados observables entre dos usuarios autenticados', async ({ page, context }) => {
  await mockMarketApi(context)
  await authenticate(page, 'usr_user-a')
  await page.goto('/market')
  await expect(page.getByLabel('Saldo disponible')).toContainText('1.500,00')

  const secondPage = await context.newPage()
  await authenticate(secondPage, 'usr_user-b')
  await secondPage.goto('/market')
  await expect(secondPage.getByLabel('Saldo disponible')).toContainText('750,00')
  await expect(secondPage.getByLabel('Saldo disponible')).not.toContainText('1.500,00')

  const marketRequests = []
  page.on('request', (request) => {
    if (request.url().includes('/api/v1/market/')) marketRequests.push(request.url())
  })
  await page.reload()
  await expect(page.getByLabel('Saldo disponible')).toContainText('1.500,00')
  expect(marketRequests.every((url) => !url.includes('userId'))).toBe(true)
  await secondPage.close()
})
