import { cleanup, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { clearSession, saveJwt } from '../session/storage.js'
import { SessionProvider } from '../session/SessionContext.jsx'
import MarketTransactionsPage from './MarketTransactionsPage.jsx'

const api = vi.hoisted(() => ({ getMarketTransactions: vi.fn() }))
vi.mock('../api/http.js', () => api)
vi.mock('../components/MarketTransactionActivityChart.jsx', () => ({ default: () => <div data-testid="activity-chart" /> }))

const PLAYER_ID = '11111111-1111-4111-8111-111111111111'

function transaction (id, operationType = 'PURCHASE') {
  return {
    transaction_id: `tx-${id}`,
    player_id: PLAYER_ID,
    player_name_snapshot: 'Jugador 1',
    operation_type: operationType,
    confirmed_at: '2026-10-29T12:00:00Z',
    quantity: 2,
    unit_price: 10,
    total_amount: 20,
    quote_version: 1
  }
}

function response (page = 1, limit = 10, totalRecords = 27) {
  const totalPages = Math.ceil(totalRecords / limit)
  return {
    data: {
      transactions: Array.from({ length: Math.min(limit, totalRecords - ((page - 1) * limit)) }, (_, index) => transaction(((page - 1) * limit) + index + 1)),
      pagination: { current_page: page, page_size: limit, total_pages: totalPages, total_records: totalRecords }
    }
  }
}

function renderPage () {
  return render(<SessionProvider><MarketTransactionsPage /></SessionProvider>)
}

describe('MarketTransactionsPage', () => {
  afterEach(() => {
    cleanup()
    clearSession()
    vi.clearAllMocks()
  })

  function authenticate () {
    saveJwt(`header.${btoa(JSON.stringify({ exp: Math.floor(Date.now() / 1000) + 3600 }))}.signature`)
  }

  it('carga inicialmente diez registros y cambia el límite volviendo a la primera página', async () => {
    authenticate()
    api.getMarketTransactions.mockImplementation(({ filters }) => Promise.resolve(response(filters.page, filters.limit)))
    renderPage()

    await screen.findByText('Mostrando 1–10 de 27 transacciones')
    expect(api.getMarketTransactions).toHaveBeenCalledWith(expect.objectContaining({ filters: expect.objectContaining({ page: 1, limit: 10 }) }))

    const user = userEvent.setup()
    await user.selectOptions(screen.getByLabelText('Transacciones por página'), '20')
    await screen.findByText('Mostrando 1–20 de 27 transacciones')
    expect(api.getMarketTransactions).toHaveBeenLastCalledWith(expect.objectContaining({ filters: expect.objectContaining({ page: 1, limit: 20 }) }))
  })

  it('navega con anterior, siguiente y números, mostrando una ventana de páginas', async () => {
    authenticate()
    api.getMarketTransactions.mockImplementation(({ filters }) => Promise.resolve(response(filters.page, filters.limit, 127)))
    renderPage()
    await screen.findByText('Mostrando 1–10 de 127 transacciones')

    const user = userEvent.setup()
    await user.click(screen.getByRole('button', { name: 'Siguiente' }))
    await screen.findByText('Mostrando 11–20 de 127 transacciones')
    expect(screen.getByRole('button', { name: 'Anterior' })).toBeEnabled()
    expect(screen.getByRole('button', { name: '1' })).toBeEnabled()
    expect(api.getMarketTransactions).toHaveBeenLastCalledWith(expect.objectContaining({ filters: expect.objectContaining({ page: 2, limit: 10 }) }))

    await user.click(screen.getByRole('button', { name: '5' }))
    await screen.findByText('Mostrando 41–50 de 127 transacciones')
    expect(screen.getAllByText('…').length).toBeGreaterThan(0)
  })

  it('deshabilita la navegación en los extremos', async () => {
    authenticate()
    api.getMarketTransactions.mockResolvedValue(response(1, 10, 10))
    renderPage()
    await screen.findByText('Mostrando 1–10 de 10 transacciones')

    expect(screen.getByRole('button', { name: 'Anterior' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Siguiente' })).toBeDisabled()
    expect(screen.getByRole('button', { name: '1' })).toBeDisabled()
  })

  it('conserva filtros editados sin consultar hasta aplicar y los conserva al paginar', async () => {
    authenticate()
    api.getMarketTransactions.mockImplementation(({ filters }) => Promise.resolve(response(filters.page, filters.limit)))
    renderPage()
    await screen.findByText('Mostrando 1–10 de 27 transacciones')

    const user = userEvent.setup()
    await user.selectOptions(screen.getByLabelText('Tipo'), 'SALE')
    expect(api.getMarketTransactions).toHaveBeenCalledTimes(1)
    await user.click(screen.getByRole('button', { name: 'Aplicar filtros' }))
    await waitFor(() => expect(api.getMarketTransactions).toHaveBeenLastCalledWith(expect.objectContaining({ filters: expect.objectContaining({ page: 1, type: 'SALE' }) })))
    await user.click(screen.getByRole('button', { name: 'Siguiente' }))
    await waitFor(() => expect(api.getMarketTransactions).toHaveBeenLastCalledWith(expect.objectContaining({ filters: expect.objectContaining({ page: 2, type: 'SALE' }) })))
  })

  it('mantiene filtros y tarjetas montados mientras actualiza', async () => {
    authenticate()
    let resolveNext
    api.getMarketTransactions.mockImplementation(({ filters }) => filters.page === 1
      ? Promise.resolve(response(1, filters.limit))
      : new Promise(resolve => { resolveNext = resolve }))
    renderPage()
    await screen.findByText('Mostrando 1–10 de 27 transacciones')

    await userEvent.setup().click(screen.getByRole('button', { name: 'Siguiente' }))
    expect(screen.getByRole('heading', { name: 'Historial de transacciones' })).toBeInTheDocument()
    expect(screen.getByText('Actualizando transacciones…')).toBeInTheDocument()
    expect(screen.getAllByRole('article').length).toBeGreaterThan(0)
    resolveNext(response(2, 10))
    await screen.findByText('Mostrando 11–20 de 27 transacciones')
  })

  it('conserva los datos anteriores cuando falla una actualización', async () => {
    authenticate()
    api.getMarketTransactions
      .mockResolvedValueOnce(response(1, 10))
      .mockRejectedValueOnce({ status: 503, message: 'Servicio temporalmente no disponible' })
    renderPage()
    await screen.findByText('Mostrando 1–10 de 27 transacciones')

    await userEvent.setup().click(screen.getByRole('button', { name: 'Siguiente' }))
    await screen.findByRole('alert')
    expect(screen.getAllByRole('article').length).toBeGreaterThan(0)
    expect(screen.getByText('Mostrando 1–10 de 27 transacciones')).toBeInTheDocument()
  })
})
