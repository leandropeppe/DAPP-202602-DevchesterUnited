import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import MarketOperationPanel from '../src/components/MarketOperationPanel.jsx'
import MarketPortfolioPage from '../src/views/MarketPortfolioPage.jsx'
import MarketTransactionsPage from '../src/views/MarketTransactionsPage.jsx'

const marketApi = vi.hoisted(() => ({
  purchaseMarketTokens: vi.fn(),
  sellMarketTokens: vi.fn(),
  getMarketOperation: vi.fn(),
  getMarketPortfolio: vi.fn(),
  getMarketTransactions: vi.fn()
}))

vi.mock('../src/api/http.js', () => marketApi)
vi.mock('../src/session/SessionContext.jsx', () => ({
  useSession: () => ({ jwt: 'jwt-test', isJwtExpired: false })
}))

const player = {
  id: 'ply_11111111-1111-4111-8111-111111111111',
  name: 'Jugador de prueba',
  league: 'Liga de prueba',
  team: 'Equipo de prueba',
  player_active: true,
  team_active: true,
  tokens_available: 100,
  quote_id: '22222222-2222-4222-8222-222222222222',
  quote_version: 3,
  price_per_token: 10,
  quote_status: 'VALID',
  quote_availability: 'CURRENT',
  operable: true
}

const completeOperation = {
  status: 'CONFIRMED',
  updated_at: '2026-10-19T12:00:00Z',
  total_amount: 1000
}

beforeEach(() => {
  vi.clearAllMocks()
  marketApi.purchaseMarketTokens.mockResolvedValue({ data: completeOperation })
  marketApi.getMarketPortfolio.mockResolvedValue({
    data: { open_positions: [], realized_pnl: 0, unrealized_pnl: null, combined_result: null, combined_result_complete: false }
  })
  marketApi.getMarketTransactions.mockResolvedValue({
    data: { transactions: [], pagination: { current_page: 1, page_size: 20, total_pages: 0, total_records: 0 } }
  })
})

describe('validaciones del flujo de operaciones del mercado', () => {
  it('permite 100 tokens y envía la cantidad exacta', async () => {
    const user = userEvent.setup()
    render(<MarketOperationPanel player={player} type="PURCHASE" jwt="jwt-test" onClose={vi.fn()} />)

    await user.clear(screen.getByRole('spinbutton'))
    await user.type(screen.getByRole('spinbutton'), '100')
    await user.click(screen.getByRole('checkbox'))
    await user.click(screen.getByRole('button', { name: /confirmar compra/i }))

    await waitFor(() => expect(marketApi.purchaseMarketTokens).toHaveBeenCalledTimes(1))
    expect(marketApi.purchaseMarketTokens.mock.calls[0][0].body.quantity).toBe(100)
  })

  it('rechaza 101 tokens y no envía ninguna solicitud', async () => {
    const user = userEvent.setup()
    render(<MarketOperationPanel player={player} type="PURCHASE" jwt="jwt-test" onClose={vi.fn()} />)

    await user.clear(screen.getByRole('spinbutton'))
    await user.type(screen.getByRole('spinbutton'), '101')

    expect(screen.getByRole('alert')).toHaveTextContent('100')
    expect(screen.getByRole('button', { name: /confirmar compra/i })).toBeDisabled()
    expect(marketApi.purchaseMarketTokens).not.toHaveBeenCalled()
  })

  it('conserva la clave y permite recuperar una respuesta de operación incompleta', async () => {
    const user = userEvent.setup()
    marketApi.purchaseMarketTokens.mockResolvedValueOnce({ data: { updated_at: '2026-10-19T12:00:00Z' } })
    marketApi.getMarketOperation.mockResolvedValueOnce({
      data: {
        idempotency_key: 'recovered-key',
        operation_type: 'PURCHASE',
        status: 'CONFIRMED',
        updated_at: '2026-10-19T12:01:00Z'
      }
    })
    render(<MarketOperationPanel player={player} type="PURCHASE" jwt="jwt-test" onClose={vi.fn()} />)

    await user.click(screen.getByRole('checkbox'))
    await user.click(screen.getByRole('button', { name: /confirmar compra/i }))
    expect(await screen.findByText(/respuesta incompleta/i)).toBeInTheDocument()

    const submittedKey = marketApi.purchaseMarketTokens.mock.calls[0][0].idempotencyKey
    await user.click(screen.getByRole('button', { name: /consultar estado/i }))

    await waitFor(() => expect(marketApi.getMarketOperation).toHaveBeenCalledWith(submittedKey, 'jwt-test'))
  })

  it('no trata un HTTP 200 sin data como una operación exitosa', async () => {
    const user = userEvent.setup()
    marketApi.purchaseMarketTokens.mockResolvedValueOnce({})
    render(<MarketOperationPanel player={player} type="PURCHASE" jwt="jwt-test" onClose={vi.fn()} />)

    await user.click(screen.getByRole('checkbox'))
    await user.click(screen.getByRole('button', { name: /confirmar compra/i }))

    expect(await screen.findByText(/respuesta incompleta/i)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /consultar estado/i })).toBeInTheDocument()
  })
})

describe('validación estructural de portfolio e historial', () => {
  it('no presenta como vacío un portfolio sin estructura obligatoria', async () => {
    marketApi.getMarketPortfolio.mockResolvedValueOnce({ data: { open_positions: [] } })
    render(<MarketPortfolioPage />)

    expect(await screen.findByRole('alert')).toHaveTextContent(/portfolio incompleto/i)
    expect(screen.queryByText(/posiciones abiertas/i)).not.toBeInTheDocument()
  })

  it('acepta campos opcionales nulos en un portfolio válido', async () => {
    render(<MarketPortfolioPage />)

    expect(await screen.findByRole('heading', { name: /mi portfolio/i })).toBeInTheDocument()
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it('no presenta como vacío un historial sin paginación obligatoria', async () => {
    marketApi.getMarketTransactions.mockResolvedValueOnce({ data: { transactions: [] } })
    render(<MarketTransactionsPage />)

    expect(await screen.findByRole('alert')).toHaveTextContent(/historial incompleto/i)
    expect(screen.queryByText(/no hay transacciones confirmadas/i)).not.toBeInTheDocument()
  })

  it('no clasifica un tipo de operación desconocido como venta', async () => {
    marketApi.getMarketTransactions.mockResolvedValueOnce({
      data: {
        transactions: [{
          transaction_id: 'tx-1',
          confirmed_at: '2026-10-19T12:00:00Z',
          player_name_snapshot: 'Jugador',
          player_id: '11111111-1111-4111-8111-111111111111',
          operation_type: 'FUTURE_OPERATION',
          quantity: 1,
          unit_price: 10,
          total_amount: 10,
          quote_version: 1,
          realized_pnl: null
        }],
        pagination: { current_page: 1, page_size: 20, total_pages: 1, total_records: 1 }
      }
    })
    render(<MarketTransactionsPage />)

    expect(await screen.findByText(/tipo de operación no reconocido/i)).toBeInTheDocument()
    expect(screen.queryByText('Venta')).not.toBeInTheDocument()
  })
})
