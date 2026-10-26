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

async function mockMarketApi (context, { pendingPurchase = false, saleStatus = 'REJECTED' } = {}) {
  let purchaseStatus = pendingPurchase ? 'PROCESSING' : 'CONFIRMED'
  let saleCount = 0

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
      if (saleStatus === 'CONFIRMED') saleCount += 1
      await route.fulfill(success({ status: saleStatus, updated_at: '2026-10-19T12:00:00Z', total_amount: saleStatus === 'CONFIRMED' ? 12.5 : null }))
      return
    }

    if (url.pathname.includes('/operations/')) {
      purchaseStatus = 'CONFIRMED'
      await route.fulfill(success({ idempotency_key: decodeURIComponent(url.pathname.split('/').pop()), operation_type: 'PURCHASE', status: purchaseStatus, updated_at: '2026-10-19T12:01:00Z' }))
      return
    }

    if (url.pathname.endsWith('/portfolio')) {
      const quantity = saleStatus === 'CONFIRMED' ? Math.max(0, 2 - saleCount) : 2
      await route.fulfill(success({
        open_positions: quantity > 0 ? [{
          player_id: '11111111-1111-4111-8111-111111111111',
          player_name: 'Jugador vigente',
          team: 'Equipo',
          league: 'Liga',
          quantity,
          pending_acquisition_cost: quantity * 10,
          weighted_average_cost: 10,
          quote_id: currentPlayer.quote_id,
          quote_version: currentPlayer.quote_version,
          quote_availability: 'CURRENT',
          unit_price: 12.5,
          current_value: quantity * 12.5,
          unrealized_pnl: quantity * 2.5
        }] : [],
        open_players: quantity > 0 ? 1 : 0,
        total_tokens: quantity,
        total_pending_acquisition_cost: quantity * 10,
        total_current_value: quantity * 12.5,
        realized_pnl: userKey === 'A' ? 4.5 : 1.5,
        unrealized_pnl: quantity * 2.5,
        combined_result: quantity * 2.5 + (userKey === 'A' ? 4.5 : 1.5),
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
  await expect(page.getByText(/Cotización histórica/)).toBeVisible()
  await expect(page.getByText(/Cotización no disponible/)).toBeVisible()

  const currentCard = page.locator('article.market-player-card').filter({ hasText: 'Jugador vigente' })
  await currentCard.getByRole('button', { name: 'Comprar' }).click()
  await expect(page.getByText(/Versi.*aceptada/)).toBeVisible()
  await expect(page.getByText('Total estimado')).toBeVisible()
  await page.getByRole('checkbox').check()
  await page.getByRole('button', { name: /confirmar compra/i }).click()
  await expect(page.locator('.market-operation-result').getByRole('status')).toContainText('fue confirmada')

  await page.getByRole('button', { name: 'Cerrar', exact: true }).click()
  await expect(currentCard.getByRole('button', { name: 'Vender' })).toHaveCount(0)
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
  await expect(page.locator('article.market-transaction-card').filter({ hasText: /Jugador hist.*rico A/ })).toBeVisible()
  await page.getByRole('combobox', { name: 'Tipo' }).selectOption('SALE')
  await page.getByRole('button', { name: 'Aplicar filtros' }).click()
  await expect(page.getByText(/gina 1 de 2/)).toBeVisible()
  await page.getByRole('button', { name: 'Siguiente' }).click()
  await expect(page.getByText(/gina 2 de 2/)).toBeVisible()
})

test('vende parcialmente y luego cierra una posición desde Portfolio', async ({ page, context }) => {
  await authenticate(page, 'usr_user-a')
  await mockMarketApi(context, { saleStatus: 'CONFIRMED' })
  await page.goto('/market/portfolio')

  const saleRequests = []
  const saleResponses = []
  page.on('request', (request) => {
    if (request.url().includes('/api/v1/market/sales') && request.method() === 'POST') saleRequests.push(request)
  })
  page.on('response', async (response) => {
    if (!response.url().includes('/api/v1/market/sales')) return
    const payload = await response.json()
    saleResponses.push(payload.data?.status)
  })
  const position = page
      .locator('article.market-position-card')
      .filter({ hasText: 'Jugador vigente' })

  await expect(position).toBeVisible()

  await position
      .getByRole('button', { name: 'Vender' })
      .click()

  await expect(
      page.getByRole('heading', { name: 'Confirmar venta' })
  ).toBeVisible()

  await page
      .getByRole('button', { name: /confirmar venta/i })
      .click()

  await expect(
      page.getByRole('heading', { name: 'Venta realizada' })
  ).toBeVisible()

  await expect.poll(
      () => saleRequests.length
  ).toBe(1)

  await expect.poll(
      () => saleResponses.length
  ).toBe(1)

  expect(
      saleResponses[0]
  ).toBe('CONFIRMED')

  await expect(
      page
          .locator('.market-position-card')
          .filter({ hasText: 'Jugador vigente' })
  ).toContainText(
      '1 tokens',
      {
        timeout: 5000
      }
  )

  const remainingPosition = page
      .locator('article.market-position-card')
      .filter({ hasText: 'Jugador vigente' })

  await remainingPosition
      .getByRole('button', { name: 'Vender' })
      .click()

  await expect(
      page.getByRole('heading', { name: 'Confirmar venta' })
  ).toBeVisible()

  await page
      .getByRole('button', { name: /confirmar venta/i })
      .click()

  await expect(
      page.getByRole('heading', { name: 'Venta realizada' })
  ).toBeVisible()

  await expect.poll(
      () => saleRequests.length
  ).toBe(2)

  await expect.poll(
      () => saleResponses.length
  ).toBe(2)

  expect(
      saleResponses[1]
  ).toBe('CONFIRMED')

  await expect(
      page.locator('output.empty-state')
  ).toHaveText(
      /No ten.*posiciones abiertas/i,
      {
        timeout: 5000
      }
  )

  await expect(
      page.locator('.market-position-card')
  ).toHaveCount(
      0,
      {
        timeout: 5000
      }
  )
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






