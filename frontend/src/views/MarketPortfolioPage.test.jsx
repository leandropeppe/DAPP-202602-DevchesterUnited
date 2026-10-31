import { cleanup, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { SessionProvider } from '../session/SessionContext.jsx'
import { clearSession, saveJwt } from '../session/storage.js'
import MarketPortfolioPage from './MarketPortfolioPage.jsx'

const api = vi.hoisted(() => ({ getMarketPortfolio: vi.fn() }))
vi.mock('../api/http.js', () => api)
vi.mock('../components/MarketPortfolioCharts.jsx', () => ({ default: () => <div data-testid="portfolio-charts" /> }))

const baseResponse = {
  data: {
    open_positions: [],
    realized_pnl: 0,
    unrealized_pnl: 0,
    combined_result: 0,
    combined_result_complete: true,
    open_players: 0,
    total_tokens: 0,
    total_pending_acquisition_cost: 0,
    total_current_value: 0
  }
}

function validJwt () {
  const payload = btoa(JSON.stringify({ exp: Math.floor(Date.now() / 1000) + 3600 }))
  return `header.${payload}.signature`
}

function renderPage (onNavigate = vi.fn()) {
  return render(<SessionProvider><MarketPortfolioPage onNavigate={onNavigate} /></SessionProvider>)
}

describe('MarketPortfolioPage', () => {
  afterEach(() => {
    cleanup()
    clearSession()
    vi.clearAllMocks()
  })

  it('muestra el estado de carga mientras consulta el portfolio', async () => {
    saveJwt(validJwt())
    api.getMarketPortfolio.mockReturnValue(new Promise(() => {}))
    renderPage()

    expect(screen.getByText(/Cargando informaci/)).toBeInTheDocument()
  })

  it('muestra portfolio vacio sin inventar posiciones', async () => {
    saveJwt(validJwt())
    api.getMarketPortfolio.mockResolvedValue(baseResponse)
    renderPage()

    expect(await screen.findByText(/No ten/)).toBeInTheDocument()
    expect(screen.queryByText(/tokens$/i)).not.toBeInTheDocument()
  })

  it('muestra total, reservado y disponible y permite publicar solo lo disponible', async () => {
    saveJwt(validJwt())
    api.getMarketPortfolio.mockResolvedValue({
      data: {
        ...baseResponse.data,
        open_players: 1,
        total_tokens: 10,
        open_positions: [{
          player_id: 'player-1',
          player_name: 'Jugador 1',
          total_quantity: 10,
          reserved_quantity: 4,
          available_quantity: 6,
          quote_availability: 'CURRENT',
          quote_id: 'quote-1',
          quote_version: 2,
          unit_price: 12.5,
          pending_acquisition_cost: 40,
          weighted_average_cost: 4,
          current_value: 75,
          unrealized_pnl: 35
        }]
      }
    })
    const onNavigate = vi.fn()
    renderPage(onNavigate)

    const position = await screen.findByRole('heading', { name: 'Jugador 1' })
    const card = position.closest('article')
    expect(card).toHaveTextContent('Total')
    expect(card).toHaveTextContent('10')
    expect(card).toHaveTextContent('Reservados')
    expect(card).toHaveTextContent('4')
    expect(card).toHaveTextContent('Disponibles')
    expect(card).toHaveTextContent('6')
    await userEvent.click(screen.getByRole('button', { name: 'Publicar oferta P2P' }))
    expect(onNavigate).toHaveBeenCalledWith('/market/p2p/offers?publishPlayerId=player-1')
  })

  it('no muestra la accion de publicar si no hay cantidad disponible', async () => {
    saveJwt(validJwt())
    api.getMarketPortfolio.mockResolvedValue({
      data: {
        ...baseResponse.data,
        open_positions: [{
          player_id: 'player-2',
          player_name: 'Jugador reservado',
          total_quantity: 4,
          reserved_quantity: 4,
          available_quantity: 0,
          quote_availability: 'CURRENT',
          quote_id: 'quote-2',
          quote_version: 1,
          unit_price: 8
        }]
      }
    })
    renderPage()

    await screen.findByRole('heading', { name: 'Jugador reservado' })
    expect(screen.queryByRole('button', { name: 'Publicar oferta P2P' })).not.toBeInTheDocument()
  })

  it('muestra errores de carga sin exponer detalles internos', async () => {
    saveJwt(validJwt())
    api.getMarketPortfolio.mockRejectedValue({ status: 503 })
    renderPage()

    await waitFor(() => expect(screen.getByRole('alert')).toBeInTheDocument())
    expect(screen.getByRole('alert')).toHaveTextContent(/no est/)
    expect(screen.getByRole('alert')).not.toHaveTextContent(/password|stack|database/i)
  })
})
