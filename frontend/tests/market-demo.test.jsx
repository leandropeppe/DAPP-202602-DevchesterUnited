import { render, screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import MarketPortfolioPage from '../src/views/MarketPortfolioPage.jsx'
import MarketTransactionsPage from '../src/views/MarketTransactionsPage.jsx'

const marketApi = vi.hoisted(() => ({ getMarketPortfolio: vi.fn(), getPlayerDetail: vi.fn(), getMarketTransactions: vi.fn() }))
const marketSession = vi.hoisted(() => ({ jwt: 'jwt-demo-user-1', isJwtExpired: false }))
vi.mock('../src/api/http.js', () => marketApi)
vi.mock('../src/session/SessionContext.jsx', () => ({ useSession: () => marketSession }))

const demoPlayer = { id: 'ply_00000000-0000-4000-8000-000000000001', name: 'T073 Demo Player 1', league: 'Demo League', team: 'Demo Team', quote_id: '10000000-0000-4000-8000-000000000001' }

beforeEach(() => {
  vi.clearAllMocks()
  marketApi.getPlayerDetail.mockResolvedValue({ data: {} })
  marketApi.getMarketPortfolio.mockResolvedValue({ data: {
    open_positions: [{ ...demoPlayer, player_id: demoPlayer.id.slice(4), quantity: 6, pending_acquisition_cost: 6, weighted_average_cost: 1, quote_availability: 'CURRENT', quote_version: 2, unit_price: 1.5, current_value: 9, unrealized_pnl: 3 }],
    open_players: 1, total_tokens: 6, total_pending_acquisition_cost: 6, total_current_value: 9, realized_pnl: 2, unrealized_pnl: 3, combined_result: 5, combined_result_complete: true
  } })
  marketApi.getMarketTransactions.mockResolvedValue({ data: {
    transactions: [{ transaction_id: '50000000-0000-4000-8000-000000000001', confirmed_at: '2026-10-19T12:00:00Z', player_id: demoPlayer.id.slice(4), player_name_snapshot: demoPlayer.name, operation_type: 'SALE', quantity: 4, unit_price: 1.5, total_amount: 6, quote_id: demoPlayer.quote_id, quote_version: 2, idempotency_request_id: '60000000-0000-4000-8000-000000000001', realized_pnl: 2 }],
    pagination: { current_page: 1, page_size: 10, total_pages: 1, total_records: 1 }
  } })
})

describe('demostracion controlada T073/T074', () => {
  it('muestra portfolio e historial de la identidad autenticada con resultados de la demo', async () => {
    render(<MarketPortfolioPage />)
    expect(await screen.findByRole('heading', { name: 'Mi portfolio' })).toBeInTheDocument()
    expect(screen.getByText('P/L realizada').nextElementSibling).toHaveTextContent('2,00')
    expect(marketApi.getMarketPortfolio).toHaveBeenCalledWith('jwt-demo-user-1')

    render(<MarketTransactionsPage />)
    expect(await screen.findByRole('heading', { name: 'Historial de transacciones' })).toBeInTheDocument()
    expect(screen.getByText('Venta legacy')).toBeInTheDocument()
    const transactionSnapshot = screen.getAllByText(/T073 Demo Player 1/).at(-1)
    expect(transactionSnapshot).toHaveTextContent('T073 Demo Player 1')
    expect(marketApi.getMarketTransactions).toHaveBeenCalledWith({ jwt: 'jwt-demo-user-1', filters: { playerId: undefined, type: undefined, from: undefined, to: undefined, page: 1, limit: 10 } })
  })
})
