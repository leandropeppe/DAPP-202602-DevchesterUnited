import { render, screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import MarketPage from '../src/views/MarketPage.jsx'
import MarketPortfolioPage from '../src/views/MarketPortfolioPage.jsx'
import MarketTransactionsPage from '../src/views/MarketTransactionsPage.jsx'

const marketApi = vi.hoisted(() => ({
  getMarketBalance: vi.fn(),
  getMarketPlayers: vi.fn(),
  getMarketPortfolio: vi.fn(),
  getMarketTransactions: vi.fn()
}))
const marketSession = vi.hoisted(() => ({ jwt: 'jwt-demo-user-1', isJwtExpired: false }))

vi.mock('../src/api/http.js', () => marketApi)
vi.mock('../src/session/SessionContext.jsx', () => ({
  useSession: () => marketSession
}))

const DEMO_USERS = [
  'demo-user-1',
  'demo-user-2',
  'demo-user-3',
  'demo-user-4'
]

const DEMO_PLAYERS = [1, 2, 3, 4, 5].map((ordinal) => ({
  id: `ply_00000000-0000-4000-8000-00000000000${ordinal}`,
  name: `T073 Demo Player ${ordinal}`,
  league: 'Demo League',
  team: 'Demo Team',
  player_active: true,
  team_active: true,
  tokens_available: 100,
  operable: ordinal === 1,
  quote_status: ordinal === 1 ? 'VALID' : 'REPLACED',
  quote_availability: ordinal === 1 ? 'CURRENT' : 'HISTORICAL',
  quote_id: `10000000-0000-4000-8000-00000000000${ordinal}`,
  quote_version: ordinal === 1 ? 2 : 1,
  price_per_token: ordinal === 1 ? 1.5 : 1
}))

beforeEach(() => {
  vi.clearAllMocks()
  marketSession.jwt = 'jwt-demo-user-1'
  marketSession.isJwtExpired = false
  marketApi.getMarketBalance.mockResolvedValue({ data: { balance: 1000 } })
  marketApi.getMarketPlayers.mockResolvedValue({
    data: {
      players: DEMO_PLAYERS,
      pagination: { current_page: 1, page_size: 20, total_pages: 1, total_records: 5 }
    }
  })
  marketApi.getMarketPortfolio.mockResolvedValue({
    data: {
      open_positions: [{
        player_id: DEMO_PLAYERS[0].id.slice(4),
        quantity: 6,
        pending_acquisition_cost: 6,
        weighted_average_cost: 1,
        quote_availability: 'CURRENT',
        quote_id: DEMO_PLAYERS[0].quote_id,
        quote_version: 2,
        unit_price: 1.5,
        current_value: 9,
        unrealized_pnl: 3
      }],
      realized_pnl: 2,
      unrealized_pnl: 3,
      combined_result: 5,
      combined_result_complete: true
    }
  })
  marketApi.getMarketTransactions.mockResolvedValue({
    data: {
      transactions: [{
        transaction_id: '50000000-0000-4000-8000-000000000001',
        confirmed_at: '2026-10-19T12:00:00Z',
        player_id: DEMO_PLAYERS[0].id.slice(4),
        player_name_snapshot: DEMO_PLAYERS[0].name,
        operation_type: 'SALE',
        quantity: 4,
        unit_price: 1.5,
        total_amount: 6,
        quote_id: DEMO_PLAYERS[0].quote_id,
        quote_version: 2,
        idempotency_request_id: '60000000-0000-4000-8000-000000000001',
        realized_pnl: 2
      }],
      pagination: { current_page: 1, page_size: 20, total_pages: 1, total_records: 1 }
    }
  })
})

describe('demostracion controlada T073/T074', () => {
  it('carga cuatro usuarios de prueba como datos aislados y cinco jugadores', async () => {
    expect(DEMO_USERS).toHaveLength(4)
    render(<MarketPage onNavigate={vi.fn()} />)

    expect(await screen.findByRole('heading', { name: 'Mercado de jugadores' })).toBeInTheDocument()
    expect(screen.getByLabelText('Saldo disponible')).toHaveTextContent('1.000,00')
    for (const player of DEMO_PLAYERS) {
      expect(screen.getByText(player.name)).toBeInTheDocument()
    }
    expect(marketApi.getMarketBalance).toHaveBeenCalledWith('jwt-demo-user-1')
    expect(marketApi.getMarketPlayers).toHaveBeenCalledWith({
      jwt: 'jwt-demo-user-1',
      filters: { page: 1, limit: 20 }
    })
  })

  it('muestra portfolio e historial de la identidad autenticada con resultados de la demo', async () => {
    render(<MarketPortfolioPage />)
    expect(await screen.findByRole('heading', { name: 'Mi portfolio' })).toBeInTheDocument()
    expect(screen.getByText('P/L realizada').nextElementSibling).toHaveTextContent('2,00')
    expect(marketApi.getMarketPortfolio).toHaveBeenCalledWith('jwt-demo-user-1')

    render(<MarketTransactionsPage />)
    expect(await screen.findByRole('heading', { name: 'Historial de transacciones' })).toBeInTheDocument()
    expect(screen.getByText('Venta')).toBeInTheDocument()
    const transactionSnapshot = screen.getByText(/T073 Demo Player 1/)
    expect(transactionSnapshot).toHaveTextContent('T073 Demo Player 1')
    expect(marketApi.getMarketTransactions).toHaveBeenCalledWith({
      jwt: 'jwt-demo-user-1',
      filters: { playerId: undefined, type: undefined, from: undefined, to: undefined, page: 1, limit: 20 }
    })
  })
})
