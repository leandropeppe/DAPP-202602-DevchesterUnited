import { cleanup, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { SessionProvider } from '../session/SessionContext.jsx'
import { clearSession, saveJwt } from '../session/storage.js'
import MarketPortfolioPage from './MarketPortfolioPage.jsx'

const api = vi.hoisted(() => ({ getMarketPortfolio: vi.fn(), getPlayerDetail: vi.fn() }))
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

    expect(await screen.findByRole('alert')).toBeInTheDocument()
    expect(screen.getByRole('alert')).toHaveTextContent(/no est/)
    expect(screen.getByRole('alert')).not.toHaveTextContent(/password|stack|database/i)
  })

  it('enriquece la card por UUID con posicion, tema, silueta y valoracion del catalogo', async () => {
    saveJwt(validJwt())
    api.getMarketPortfolio.mockResolvedValue({
      data: {
        ...baseResponse.data,
        open_positions: [{
          player_id: '11111111-1111-4111-8111-111111111111',
          player_name: 'Jugador del portfolio',
          quote_availability: 'CURRENT',
          unit_price: 12.5,
          unrealized_pnl: 1
        }]
      }
    })
    api.getPlayerDetail.mockResolvedValue({ data: {
      id: 'ply_11111111-1111-4111-8111-111111111111',
      name: 'Jugador del catálogo',
      position: 'Forward',
      nationality: 'Argentina',
      valuation: 8.7
    } })

    renderPage()

    const card = await screen.findByRole('heading', { name: 'Jugador del portfolio' })
    const article = card.closest('article')
    expect(api.getPlayerDetail).toHaveBeenCalledWith('ply_11111111-1111-4111-8111-111111111111')
    await waitFor(() => expect(article).toHaveClass('player-card--purple'))
    expect(article).toHaveTextContent('Forward')
    expect(article).toHaveTextContent('8,7')
    expect(article.querySelector('.player-card__silhouette')).toHaveAttribute('src', '/images/player-silhouette-purple.png')
    expect(article).toHaveTextContent('12,50')
  })

  it('mantiene fallback y cotizacion del portfolio si falla el detalle del catalogo', async () => {
    saveJwt(validJwt())
    api.getMarketPortfolio.mockResolvedValue({
      data: {
        ...baseResponse.data,
        open_positions: [{
          player_id: '22222222-2222-4222-8222-222222222222',
          player_name: 'Jugador sin detalle',
          quote_availability: 'CURRENT',
          unit_price: 19.25,
          unrealized_pnl: 0
        }]
      }
    })
    api.getPlayerDetail.mockRejectedValue(new Error('catalog unavailable'))

    renderPage()

    const card = await screen.findByRole('heading', { name: 'Jugador sin detalle' })
    const article = card.closest('article')
    expect(article).toHaveTextContent('Posición no disponible')
    expect(article).toHaveTextContent('—')
    expect(article).toHaveTextContent('19,25')
  })

  it('muestra los siete indicadores con estados financieros y colores por signo', async () => {
    saveJwt(validJwt())
    api.getMarketPortfolio.mockResolvedValue({
      data: {
        ...baseResponse.data,
        open_players: 0,
        total_tokens: 12,
        total_pending_acquisition_cost: 80,
        total_current_value: 110,
        realized_pnl: 5,
        unrealized_pnl: -3,
        combined_result: null,
        combined_result_complete: false
      }
    })
    renderPage()

    expect(await screen.findByText('Jugadores con posición')).toBeInTheDocument()
    for (const label of ['Tokens totales', 'Costo pendiente total', 'Valor actual total', 'P/L realizada', 'P/L no realizada', 'Resultado combinado']) {
      expect(screen.getByText(label)).toBeInTheDocument()
    }
    expect(screen.getByText('P/L realizada').closest('article')).toHaveClass('market-portfolio-summary-card--positive')
    expect(screen.getByText('P/L no realizada').closest('article')).toHaveClass('market-portfolio-summary-card--negative')
    expect(screen.getByText('Jugadores con posición').closest('article')).toHaveClass('market-portfolio-summary-card--neutral')
    expect(screen.getByText('Sin información')).toBeInTheDocument()
  })

  it('pagina seis posiciones, permite cambiar a doce y reinicia en la primera página', async () => {
    saveJwt(validJwt())
    api.getMarketPortfolio.mockResolvedValue({
      data: {
        ...baseResponse.data,
        open_players: 13,
        total_tokens: 13,
        open_positions: Array.from({ length: 13 }, (_, index) => ({
          player_id: `player-${index + 1}`,
          player_name: `Jugador ${index + 1}`,
          team: 'Manchester United FC',
          league: 'Premier League',
          total_quantity: 1,
          reserved_quantity: 0,
          available_quantity: 1,
          quote_availability: 'ABSENT',
          unit_price: null,
          current_value: null,
          unrealized_pnl: null
        }))
      }
    })
    renderPage()
    expect((await screen.findAllByText('Mostrando 1–6 de 13 jugadores')).length).toBe(2)
    expect(screen.getByRole('heading', { name: 'Jugador 1' })).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Jugador 7' })).not.toBeInTheDocument()

    const user = userEvent.setup()
    await user.click(screen.getByRole('button', { name: 'Siguiente' }))
    expect((await screen.findAllByText('Mostrando 7–12 de 13 jugadores')).length).toBe(2)
    expect(screen.getByRole('heading', { name: 'Jugador 7' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Anterior' })).toBeEnabled()

    await user.selectOptions(screen.getByLabelText('Jugadores por página'), '12')
    expect((await screen.findAllByText('Mostrando 1–12 de 13 jugadores')).length).toBe(2)
    expect(screen.getByRole('heading', { name: 'Jugador 1' })).toBeInTheDocument()
  })
})
