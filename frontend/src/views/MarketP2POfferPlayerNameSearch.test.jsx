import { cleanup, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { clearSession, saveJwt } from '../session/storage.js'
import { SessionProvider } from '../session/SessionContext.jsx'
import MarketP2POffersPage from './MarketP2POffersPage.jsx'

const api = vi.hoisted(() => ({
  getMarketP2POffers: vi.fn(),
  getMarketPortfolio: vi.fn()
}))

vi.mock('../api/http.js', () => api)

async function preparePage () {
  saveJwt(`header.${btoa(JSON.stringify({ exp: Math.floor(Date.now() / 1000) + 3600 }))}.signature`)
  mockMarket()
  renderPage()
  await screen.findByRole('heading', { name: 'Federico Valverde' })
}

function renderPage () {
  return render(<SessionProvider><MarketP2POffersPage /></SessionProvider>)
}

function mockMarket (pagination = {}) {
  api.getMarketPortfolio.mockResolvedValue({ data: { open_positions: [] } })
  api.getMarketP2POffers.mockResolvedValue({ data: {
    offers: [{ offer_id: 'offer-1', player_id: 'player-1', player_name: 'Federico Valverde', status: 'SUSPENDED', mine: false, remaining_quantity: 1, quote: null }],
    pagination: { current_page: 1, page_size: 20, total_pages: 2, total_records: 21, ...pagination }
  } })
}

describe('MarketP2POffersPage filtros explícitos', () => {
  afterEach(() => {
    cleanup()
    clearSession()
    vi.clearAllMocks()
  })

  it('no consulta mientras se editan nombre, estado o mis ofertas', async () => {
    await preparePage()
    const initialCalls = api.getMarketP2POffers.mock.calls.length
    const user = userEvent.setup()

    await user.type(screen.getByPlaceholderText('Buscar por nombre'), 'val')
    await user.selectOptions(screen.getByLabelText('Estado'), 'CANCELLED')
    await user.click(screen.getByLabelText('Solo mis ofertas'))

    expect(api.getMarketP2POffers).toHaveBeenCalledTimes(initialCalls)
  })

  it('Buscar y Enter aplican los valores visibles con nombre recortado', async () => {
    await preparePage()
    const user = userEvent.setup()
    await user.type(screen.getByPlaceholderText('Buscar por nombre'), '  valverde  ')
    await user.selectOptions(screen.getByLabelText('Estado'), 'CANCELLED')
    await user.click(screen.getByLabelText('Solo mis ofertas'))
    await user.click(screen.getByRole('button', { name: 'Buscar' }))

    await waitFor(() => expect(api.getMarketP2POffers).toHaveBeenLastCalledWith(expect.objectContaining({
      filters: expect.objectContaining({ playerName: 'valverde', status: 'CANCELLED', mine: 'true', page: 1, limit: 20 })
    })))

    await user.clear(screen.getByPlaceholderText('Buscar por nombre'))
    await user.type(screen.getByPlaceholderText('Buscar por nombre'), 'federico')
    await user.keyboard('{Enter}')
    await waitFor(() => expect(api.getMarketP2POffers).toHaveBeenLastCalledWith(expect.objectContaining({
      filters: expect.objectContaining({ playerName: 'federico', status: 'CANCELLED', mine: 'true', page: 1, limit: 20 })
    })))
  })

  it('Limpiar restablece el formulario y consulta sin filtros', async () => {
    await preparePage()
    const user = userEvent.setup()
    await user.type(screen.getByPlaceholderText('Buscar por nombre'), 'valverde')
    await user.selectOptions(screen.getByLabelText('Estado'), 'CANCELLED')
    await user.click(screen.getByLabelText('Solo mis ofertas'))
    await user.click(screen.getByRole('button', { name: 'Limpiar' }))

    expect(screen.getByPlaceholderText('Buscar por nombre')).toHaveValue('')
    expect(screen.getByLabelText('Estado')).toHaveValue('')
    expect(screen.getByLabelText('Solo mis ofertas')).not.toBeChecked()
    await waitFor(() => expect(api.getMarketP2POffers).toHaveBeenLastCalledWith(expect.objectContaining({
      filters: expect.objectContaining({ playerName: undefined, status: undefined, mine: undefined, page: 1, limit: 20 })
    })))
  })

  it('la paginación reutiliza los filtros aplicados y no los valores editados', async () => {
    await preparePage()
    const user = userEvent.setup()
    await user.type(screen.getByPlaceholderText('Buscar por nombre'), 'valverde')
    await user.click(screen.getByRole('button', { name: 'Buscar' }))
    await waitFor(() => expect(api.getMarketP2POffers).toHaveBeenLastCalledWith(expect.objectContaining({
      filters: expect.objectContaining({ playerName: 'valverde', page: 1, limit: 20 })
    })))

    await user.clear(screen.getByPlaceholderText('Buscar por nombre'))
    await user.click(screen.getByRole('button', { name: 'Siguiente' }))
    await waitFor(() => expect(api.getMarketP2POffers).toHaveBeenLastCalledWith(expect.objectContaining({
      filters: expect.objectContaining({ playerName: 'valverde', page: 2, limit: 20 })
    })))
  })
})
