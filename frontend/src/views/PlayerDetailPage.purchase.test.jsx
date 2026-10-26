import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import PlayerDetailPage from './PlayerDetailPage.jsx'
import { SessionProvider } from '../session/SessionContext.jsx'
import { saveJwt } from '../session/storage.js'

const api = vi.hoisted(() => ({
  getPlayerDetail: vi.fn(),
  getPlayerValuationHistory: vi.fn(),
  getMarketBalance: vi.fn(),
  getMarketPlayer: vi.fn(),
  getMarketPortfolio: vi.fn(),
  purchaseMarketTokens: vi.fn(),
  getMarketOperation: vi.fn()
}))

vi.mock('../api/http.js', () => api)

const playerId = 'ply_11111111-1111-4111-8111-111111111111'
const player = {
  id: playerId,
  name: 'Jugador Comprable',
  league: 'Liga',
  team: 'Equipo',
  position: 'Forward',
  nationality: 'Argentina',
  valuation: 60,
  statistics: { generales: { minsPlayed: 900, rating: 7.2 }, ofensivas: { goal: 8 } }
}
const marketPlayer = {
  id: playerId,
  name: player.name,
  league: player.league,
  team: player.team,
  player_active: true,
  team_active: true,
  quote_id: '22222222-2222-4222-8222-222222222222',
  quote_version: 4,
  price_per_token: 10,
  quote_status: 'VALID',
  quote_availability: 'CURRENT',
  operable: true
}

function renderPage () {
  return render(<SessionProvider><PlayerDetailPage playerId={playerId} onNavigate={vi.fn()} /></SessionProvider>)
}

beforeEach(() => {
  vi.clearAllMocks()
  saveJwt('jwt-test')
  api.getPlayerDetail.mockResolvedValue({ data: player })
  api.getMarketBalance.mockResolvedValue({ data: { balance: 100 } })
  api.getMarketPlayer.mockResolvedValue({ data: marketPlayer })
  api.getMarketPortfolio.mockResolvedValue({ data: { open_positions: [] } })
  api.purchaseMarketTokens.mockResolvedValue({ data: { status: 'CONFIRMED', updated_at: '2026-10-25T12:00:00Z' } })
})

afterEach(() => window.sessionStorage.clear())

describe('compra integrada en el detalle del jugador', () => {
  it('muestra precio, tenencia, saldo y calcula el costo total', async () => {
    renderPage()
    const purchase = (await screen.findByRole('heading', { name: 'Comprar tokens' })).closest('section')
    expect(within(purchase).getAllByText('10,00').length).toBeGreaterThan(0)
    expect(within(purchase).getByText('100,00 créditos')).toBeInTheDocument()
    expect(within(purchase).getByText('0')).toBeInTheDocument()

    const quantity = screen.getByRole('spinbutton')
    expect(quantity).toHaveValue(1)
    fireEvent.click(within(purchase).getByRole('button', { name: 'Aumentar cantidad' }))
    expect(quantity).toHaveValue(2)
    fireEvent.click(within(purchase).getByRole('button', { name: 'Aumentar cantidad' }))
    expect(quantity).toHaveValue(3)
    expect(within(purchase).getByText('30,00')).toBeInTheDocument()
    fireEvent.click(within(purchase).getByRole('button', { name: 'Disminuir cantidad' }))
    expect(quantity).toHaveValue(2)
    fireEvent.change(quantity, { target: { value: '10' } })
    expect(within(purchase).getByText('100,00')).toBeInTheDocument()
  })

  it('ejecuta la compra y vuelve a consultar saldo y tenencia', async () => {
    api.getMarketBalance.mockResolvedValueOnce({ data: { balance: 100 } }).mockResolvedValue({ data: { balance: 70 } })
    api.getMarketPortfolio.mockResolvedValueOnce({ data: { open_positions: [] } }).mockResolvedValue({ data: { open_positions: [{ player_id: playerId, quantity: 3 }] } })
    renderPage()
    await screen.findByRole('heading', { name: 'Comprar tokens' })
    fireEvent.change(screen.getByRole('spinbutton'), { target: { value: '3' } })
    fireEvent.click(screen.getByRole('button', { name: 'Comprar tokens' }))
    fireEvent.click(screen.getByRole('button', { name: 'Confirmar compra' }))

    await waitFor(() => expect(api.purchaseMarketTokens).toHaveBeenCalledWith(expect.objectContaining({
      body: expect.objectContaining({ player_id: '11111111-1111-4111-8111-111111111111', quantity: 3 }),
      jwt: 'jwt-test'
    })))
    await waitFor(() => expect(api.getMarketBalance).toHaveBeenCalledTimes(2))
  })

  it('deshabilita la compra cuando el saldo no alcanza', async () => {
    api.getMarketBalance.mockResolvedValue({ data: { balance: 5 } })
    renderPage()
    const purchase = (await screen.findByRole('heading', { name: 'Comprar tokens' })).closest('section')
    expect(within(purchase).getByRole('button', { name: 'Comprar tokens' })).toBeDisabled()
    expect(within(purchase).getByRole('alert')).toHaveTextContent(/saldo insuficiente/i)
  })
})
