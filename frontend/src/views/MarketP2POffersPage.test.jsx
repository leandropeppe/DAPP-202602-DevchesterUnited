import { cleanup, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { clearSession, saveJwt } from '../session/storage.js'
import { SessionProvider } from '../session/SessionContext.jsx'
import MarketP2POffersPage from './MarketP2POffersPage.jsx'

const api = vi.hoisted(() => ({ getMarketP2POffers: vi.fn(), getMarketPortfolio: vi.fn(), publishMarketP2POffer: vi.fn(), purchaseMarketP2POffer: vi.fn(), cancelMarketP2POffer: vi.fn() }))
vi.mock('../api/http.js', () => api)

function renderPage () {
  return render(<SessionProvider><MarketP2POffersPage /></SessionProvider>)
}

describe('MarketP2POffersPage', () => {
  afterEach(() => {
    cleanup()
    clearSession()
    vi.clearAllMocks()
  })

  function mockPortfolio () {
    api.getMarketPortfolio.mockResolvedValue({ data: { open_positions: [{ player_id: 'player-1', player_name: 'Jugador 1', quantity: 4, quote_id: 'quote-1', quote_version: 2, unit_price: 12.5 }] } })
  }

  it('muestra estados, alias público y paginación sin habilitar operaciones', async () => {
    saveJwt(`header.${btoa(JSON.stringify({ exp: Math.floor(Date.now() / 1000) + 3600 }))}.signature`)
    mockPortfolio()
    api.getMarketP2POffers.mockResolvedValue({ data: {
      offers: [
        { offer_id: 'offer-1', player_id: 'player-1', seller_alias: 'Vendedor 1', remaining_quantity: 3, status: 'PURCHASABLE', purchasable: true, mine: true, quote: { version: 2, unit_price: 12.5 } },
        { offer_id: 'offer-2', player_id: 'player-2', seller_alias: 'Vendedor 2', remaining_quantity: 1, status: 'SUSPENDED', purchasable: false, mine: false, quote: null }
      ],
      pagination: { current_page: 1, page_size: 20, total_pages: 2, total_records: 21 }
    } })

    renderPage()

    expect(await screen.findByText('Disponible para comprar')).toBeInTheDocument()
    expect(screen.getByText('Suspendida')).toBeInTheDocument()
    expect(screen.getByText('Vendedor 2')).toBeInTheDocument()
    expect(screen.getByText('Tu oferta')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Cancelar oferta' })).toBeEnabled()
    expect(screen.getByRole('button', { name: 'Operación no disponible' })).toBeDisabled()
    expect(screen.getByText('Página 1 de 2')).toBeInTheDocument()
    expect(screen.queryByText(/correo|email|saldo/i)).not.toBeInTheDocument()
  })

  it('envía filtros al cambiar estado y opción de mis ofertas', async () => {
    saveJwt(`header.${btoa(JSON.stringify({ exp: Math.floor(Date.now() / 1000) + 3600 }))}.signature`)
    mockPortfolio()
    api.getMarketP2POffers.mockResolvedValue({ data: { offers: [], pagination: { current_page: 1, page_size: 20, total_pages: 0, total_records: 0 } } })
    renderPage()

    await waitFor(() => expect(api.getMarketP2POffers).toHaveBeenCalled())
    await import('@testing-library/user-event').then(async ({ default: userEvent }) => {
      const user = userEvent.setup()
      await user.selectOptions(screen.getByLabelText('Estado'), 'CANCELLED')
      await user.click(screen.getByLabelText('Solo mis ofertas'))
    })

    await waitFor(() => expect(api.getMarketP2POffers).toHaveBeenLastCalledWith(expect.objectContaining({
      filters: expect.objectContaining({ status: 'CANCELLED', mine: 'true', page: 1, limit: 20 })
    })))
  })

  it('publica una oferta con confirmación y una clave idempotente', async () => {
    saveJwt(`header.${btoa(JSON.stringify({ exp: Math.floor(Date.now() / 1000) + 3600 }))}.signature`)
    mockPortfolio()
    api.getMarketP2POffers.mockResolvedValue({ data: { offers: [], pagination: { current_page: 1, page_size: 20, total_pages: 0, total_records: 0 } } })
    api.publishMarketP2POffer.mockResolvedValue({ data: { offer_id: 'offer-new' } })
    renderPage()
    await screen.findByText('No hay ofertas P2P para estos filtros.')

    const user = userEvent.setup()
    await user.click(screen.getByRole('button', { name: 'Publicar oferta' }))
    await user.click(screen.getAllByRole('button', { name: 'Publicar oferta' }).at(-1))

    await waitFor(() => expect(api.publishMarketP2POffer).toHaveBeenCalledTimes(1))
    expect(api.publishMarketP2POffer.mock.calls[0][0]).toMatchObject({ body: { player_id: 'player-1', quantity: 1 }, jwt: expect.any(String), idempotencyKey: expect.any(String) })
  })

  it('compra una oferta ajena con cotización observada y no duplica el doble clic', async () => {
    saveJwt(`header.${btoa(JSON.stringify({ exp: Math.floor(Date.now() / 1000) + 3600 }))}.signature`)
    mockPortfolio()
    api.getMarketP2POffers.mockResolvedValue({ data: { offers: [{ offer_id: 'offer-buy', player_id: 'player-2', seller_alias: 'Otro', remaining_quantity: 3, status: 'PURCHASABLE', mine: false, quote: { quote_id: 'quote-1', version: 2, unit_price: 12.5 } }], pagination: { current_page: 1, page_size: 20, total_pages: 1, total_records: 1 } } })
    let resolvePurchase
    api.purchaseMarketP2POffer.mockReturnValue(new Promise((resolve) => { resolvePurchase = resolve }))
    renderPage()
    await screen.findByText('Otro')
    const user = userEvent.setup()
    await user.click(screen.getByRole('button', { name: 'Comprar oferta' }))
    const confirm = screen.getByRole('button', { name: 'Comprar tokens' })
    await user.click(confirm)
    await user.click(confirm)
    expect(api.purchaseMarketP2POffer).toHaveBeenCalledTimes(1)
    expect(api.purchaseMarketP2POffer.mock.calls[0][0]).toMatchObject({ offerId: 'offer-buy', body: { quantity: 1, quote_id: 'quote-1', quote_version: 2 }, idempotencyKey: expect.any(String) })
    resolvePurchase({ data: { status: 'FILLED' } })
  })

  it('cancela una oferta propia y renueva la clave después de QUOTE_CHANGED', async () => {
    saveJwt(`header.${btoa(JSON.stringify({ exp: Math.floor(Date.now() / 1000) + 3600 }))}.signature`)
    mockPortfolio()
    api.getMarketP2POffers.mockResolvedValue({ data: { offers: [{ offer_id: 'offer-own', player_id: 'player-1', seller_alias: 'Yo', remaining_quantity: 2, status: 'PURCHASABLE', mine: true, quote: { quote_id: 'quote-1', version: 1, unit_price: 10 } }], pagination: { current_page: 1, page_size: 20, total_pages: 1, total_records: 1 } } })
    api.cancelMarketP2POffer.mockResolvedValue({ data: { status: 'CANCELLED' } })
    renderPage()
    await screen.findByText('Tu oferta')
    const user = userEvent.setup()
    await user.click(screen.getByRole('button', { name: 'Cancelar oferta' }))
    await user.click(screen.getAllByRole('button', { name: 'Cancelar oferta' }).at(-1))
    await waitFor(() => expect(api.cancelMarketP2POffer).toHaveBeenCalledTimes(1))
    expect(api.cancelMarketP2POffer.mock.calls[0][0]).toMatchObject({ offerId: 'offer-own', idempotencyKey: expect.any(String) })
  })

  it('muestra QUOTE_CHANGED y exige una nueva clave antes de confirmar', async () => {
    saveJwt(`header.${btoa(JSON.stringify({ exp: Math.floor(Date.now() / 1000) + 3600 }))}.signature`)
    mockPortfolio()
    api.getMarketP2POffers.mockResolvedValue({ data: { offers: [{ offer_id: 'offer-buy', player_id: 'player-2', seller_alias: 'Otro', remaining_quantity: 3, status: 'PURCHASABLE', mine: false, quote: { quote_id: 'quote-1', version: 1, unit_price: 10 } }], pagination: { current_page: 1, page_size: 20, total_pages: 1, total_records: 1 } } })
    api.purchaseMarketP2POffer.mockRejectedValueOnce({ code: 'QUOTE_CHANGED', currentQuote: { quote_id: 'quote-2', version: 2, unit_price: 12 } }).mockResolvedValueOnce({ data: { status: 'FILLED' } })
    renderPage()
    await screen.findByText('Otro')
    const user = userEvent.setup()
    await user.click(screen.getByRole('button', { name: 'Comprar oferta' }))
    await user.click(screen.getByRole('button', { name: 'Comprar tokens' }))
    expect(await screen.findByText(/cotización cambió/i)).toBeInTheDocument()
    const firstKey = api.purchaseMarketP2POffer.mock.calls[0][0].idempotencyKey
    await user.click(screen.getByRole('button', { name: 'Comprar tokens' }))
    await waitFor(() => expect(api.purchaseMarketP2POffer).toHaveBeenCalledTimes(2))
    expect(api.purchaseMarketP2POffer.mock.calls[1][0].idempotencyKey).not.toBe(firstKey)
    expect(api.purchaseMarketP2POffer.mock.calls[1][0].body).toMatchObject({ quote_id: 'quote-2', quote_version: 2 })
  })
})
