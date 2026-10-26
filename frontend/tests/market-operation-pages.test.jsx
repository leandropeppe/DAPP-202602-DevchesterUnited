import { fireEvent, render, screen, waitFor, within} from '@testing-library/react'
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
    data: {
      open_positions: [],
      open_players: 0,
      total_tokens: 0,
      total_pending_acquisition_cost: 0,
      total_current_value: 0,
      realized_pnl: 0,
      unrealized_pnl: null,
      combined_result: null,
      combined_result_complete: false
    }
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

  it('abre el modal de venta del jugador seleccionado', async () => {
    const user = userEvent.setup()
    marketApi.getMarketPortfolio.mockResolvedValueOnce({
      data: {
        open_positions: [{
          player_id: '11111111-1111-4111-8111-111111111111',
          player_name: 'Jugador real',
          quantity: 3,
          quote_availability: 'CURRENT',
          quote_id: '22222222-2222-4222-8222-222222222222',
          quote_version: 4,
          unit_price: 1
        }],
        open_players: 1,
        total_tokens: 3,
        total_pending_acquisition_cost: 3,
        total_current_value: 3,
        realized_pnl: 0,
        unrealized_pnl: 0,
        combined_result: 0,
        combined_result_complete: true
      }
    })

    render(<MarketPortfolioPage />)
    await user.click(await screen.findByRole('button', { name: 'Vender' }))

    expect(screen.getByRole('dialog')).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Confirmar venta' })).toBeInTheDocument()
    expect(within(screen.getByRole('dialog')).getByText('Jugador real')).toBeInTheDocument()
  })

  it('actualiza cantidad y total, limita los controles y cancela sin vender', async () => {
    const user = userEvent.setup()
    const onClose = vi.fn()
    render(<MarketOperationPanel player={player} type="SALE" jwt="jwt-test" modalOnly onClose={onClose} availableQuantity={2} maxQuantity={2} />)

    const minus = screen.getByRole('button', { name: 'Disminuir cantidad' })
    const plus = screen.getByRole('button', { name: 'Aumentar cantidad' })
    expect(screen.getByRole('spinbutton')).toHaveValue(1)
    expect(minus).toBeDisabled()
    await user.click(plus)
    expect(screen.getByRole('spinbutton')).toHaveValue(2)
    expect(screen.getByText('20,00 créditos')).toBeInTheDocument()
    expect(plus).toBeDisabled()

    fireEvent.click(screen.getByRole('button', { name: 'Cancelar' }))
    expect(onClose).toHaveBeenCalledTimes(1)
    expect(marketApi.sellMarketTokens).not.toHaveBeenCalled()
  })

  it('acepta campos opcionales nulos en un portfolio válido', async () => {
    render(<MarketPortfolioPage />)

    expect(await screen.findByRole('heading', { name: /mi portfolio/i })).toBeInTheDocument()
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it('muestra datos canónicos y limita la venta a los tokens disponibles', async () => {
    const user = userEvent.setup()
    marketApi.getMarketPortfolio.mockResolvedValueOnce({
      data: {
        open_positions: [{
          player_id: '11111111-1111-4111-8111-111111111111',
          player_name: 'Jugador real',
          team: 'Equipo real',
          league: 'Liga real',
          quantity: 2,
          pending_acquisition_cost: 20,
          weighted_average_cost: 10,
          quote_availability: 'CURRENT',
          quote_id: '22222222-2222-4222-8222-222222222222',
          quote_version: 4,
          unit_price: 12,
          current_value: 24,
          unrealized_pnl: 4
        }],
        open_players: 1,
        total_tokens: 2,
        total_pending_acquisition_cost: 20,
        total_current_value: 24,
        realized_pnl: 0,
        unrealized_pnl: 4,
        combined_result: 4,
        combined_result_complete: true
      }
    })

    render(<MarketPortfolioPage />)
    expect(await screen.findByRole('heading', { name: 'Jugador real' })).toBeInTheDocument()
    expect(screen.getByText('Equipo real · Liga real')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Vender' }))
    expect(screen.getByRole('heading', { name: 'Confirmar venta' })).toBeInTheDocument()
    expect(screen.getByRole('spinbutton')).toHaveAttribute('max', '2')
    expect(screen.getByText(/Tokens disponibles:/)).toHaveTextContent('2')
  })

  it('rechaza 4 tokens cuando la tenencia disponible es 3 y permite 3', async () => {
    const user = userEvent.setup()
    render(<MarketOperationPanel player={player} type="SALE" jwt="jwt-test" onClose={vi.fn()} availableQuantity={3} maxQuantity={3} />)

    expect(screen.getByText('Disponible para vender: 3 tokens')).toBeInTheDocument()
    expect(screen.getByRole('spinbutton')).toHaveAttribute('max', '3')
    await user.clear(screen.getByRole('spinbutton'))
    await user.type(screen.getByRole('spinbutton'), '4')
    expect(screen.getByRole('button', { name: /confirmar venta/i })).toBeDisabled()
    expect(screen.getByRole('alert')).toHaveTextContent('3')

    await user.clear(screen.getByRole('spinbutton'))
    await user.type(screen.getByRole('spinbutton'), '3')
    expect(screen.getByRole('button', { name: /confirmar venta/i })).toBeDisabled()
    await user.click(screen.getByRole('checkbox'))
    expect(screen.getByRole('button', { name: /confirmar venta/i })).toBeEnabled()
  })

  it('mantiene el maximo de 100 cuando la tenencia supera 100 tokens', () => {
    render(<MarketOperationPanel player={player} type="SALE" jwt="jwt-test" onClose={vi.fn()} availableQuantity={150} maxQuantity={100} />)

    expect(screen.getByText('Disponible para vender: 150 tokens')).toBeInTheDocument()
    expect(screen.getByRole('spinbutton')).toHaveAttribute('max', '100')
  })

  it('recarga el portfolio después de mostrar la confirmación de venta', async () => {
    const user = userEvent.setup()

    const initialPortfolio = {
      data: {
        open_positions: [{
          player_id: '11111111-1111-4111-8111-111111111111',
          player_name: 'Jugador vendido',
          quantity: 2,
          quote_availability: 'CURRENT',
          quote_id: '22222222-2222-4222-8222-222222222222',
          quote_version: 4,
          unit_price: 1
        }],
        open_players: 1,
        total_tokens: 2,
        total_pending_acquisition_cost: 2,
        total_current_value: 2,
        realized_pnl: 0,
        unrealized_pnl: 0,
        combined_result: 0,
        combined_result_complete: true
      }
    }

    const refreshedPortfolio = {
      ...initialPortfolio,
      data: {
        ...initialPortfolio.data,
        open_positions: [{
          ...initialPortfolio.data.open_positions[0],
          quantity: 1
        }],
        total_tokens: 1,
        total_pending_acquisition_cost: 1,
        total_current_value: 1
      }
    }

    marketApi.getMarketPortfolio
        .mockResolvedValueOnce(initialPortfolio)
        .mockResolvedValueOnce(refreshedPortfolio)

    marketApi.sellMarketTokens.mockResolvedValueOnce({
      data: {
        ...completeOperation,
        status: 'CONFIRMED'
      }
    })

    render(<MarketPortfolioPage />)

    await user.click(
        await screen.findByRole('button', {
          name: 'Vender'
        })
    )

    await user.click(
        screen.getByRole('button', {
          name: /confirmar venta/i
        })
    )

    await waitFor(() =>
        expect(
            marketApi.sellMarketTokens
        ).toHaveBeenCalledWith(
            expect.objectContaining({
              body: expect.objectContaining({
                quantity: 1,
                player_id: '11111111-1111-4111-8111-111111111111'
              })
            })
        )
    )

    expect(
        await screen.findByRole('heading', {
          name: 'Venta realizada'
        })
    ).toBeInTheDocument()

    expect(
        marketApi.getMarketPortfolio
    ).toHaveBeenCalledTimes(1)

    await waitFor(
        () =>
            expect(
                marketApi.getMarketPortfolio
            ).toHaveBeenCalledTimes(2),
        {
          timeout: 3000
        }
    )

    expect(
        await screen.findByText('1 tokens')
    ).toBeInTheDocument()
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

  it('formatea fechas en la zona horaria local y muestra fallback para fechas inválidas', async () => {
    const originalDateTimeFormat = Intl.DateTimeFormat
    const dateTimeFormat = vi.spyOn(Intl, 'DateTimeFormat').mockImplementation(function (locale, options) {
      return new originalDateTimeFormat(locale, { ...options, timeZone: 'America/Argentina/Buenos_Aires' })
    })
    marketApi.getMarketTransactions.mockResolvedValueOnce({
      data: {
        transactions: [
          { transaction_id: 'tx-date', confirmed_at: '2026-10-23T01:50:54.259253Z', player_name_snapshot: 'Kenny Tete', player_id: '11111111-1111-4111-8111-111111111111', operation_type: 'SALE', quantity: 1, unit_price: 1, total_amount: 1, quote_version: 1, realized_pnl: 0 },
          { transaction_id: 'tx-null-date', confirmed_at: null, player_name_snapshot: 'Jugador sin fecha', player_id: '22222222-2222-4222-8222-222222222222', operation_type: 'PURCHASE', quantity: 1, unit_price: 1, total_amount: 1, quote_version: 1, realized_pnl: null },
          { transaction_id: 'tx-invalid-date', confirmed_at: 'fecha inválida', player_name_snapshot: 'Jugador con fecha inválida', player_id: '33333333-3333-4333-8333-333333333333', operation_type: 'SALE', quantity: 1, unit_price: 1, total_amount: 1, quote_version: 1, realized_pnl: 0 }
        ],
        pagination: { current_page: 1, page_size: 20, total_pages: 1, total_records: 3 }
      }
    })

    try {
      render(<MarketTransactionsPage />)
      expect(await screen.findByText('22/10/2026, 22:50:54')).toBeInTheDocument()
      expect(screen.getAllByText('Fecha no disponible')).toHaveLength(2)
      expect(dateTimeFormat).toHaveBeenCalledWith('es-AR', expect.objectContaining({ second: '2-digit', hour12: false }))
    } finally {
      dateTimeFormat.mockRestore()
    }
  })

  it('muestra nombres sin UUID y filtra por el player_id seleccionado', async () => {
    const user = userEvent.setup()
    const firstPlayerId = '11111111-1111-4111-8111-111111111111'
    const secondPlayerId = '22222222-2222-4222-8222-222222222222'
    marketApi.getMarketTransactions.mockResolvedValueOnce({
      data: {
        transactions: [
          { transaction_id: 'tx-1', confirmed_at: '2026-10-19T12:00:00Z', player_name_snapshot: 'Kenny Tete', player_id: firstPlayerId, operation_type: 'SALE', quantity: 1, unit_price: 1, total_amount: 1, quote_version: 1, realized_pnl: 0 },
          { transaction_id: 'tx-2', confirmed_at: '2026-10-18T12:00:00Z', player_name_snapshot: 'Kenny Tete', player_id: secondPlayerId, operation_type: 'PURCHASE', quantity: 2, unit_price: 1, total_amount: 2, quote_version: 1, realized_pnl: null },
          { transaction_id: 'tx-3', confirmed_at: '2026-10-17T12:00:00Z', player_name_snapshot: null, player_id: '33333333-3333-4333-8333-333333333333', operation_type: 'SALE', quantity: 1, unit_price: 1, total_amount: 1, quote_version: 1, realized_pnl: 0 }
        ],
        pagination: { current_page: 1, page_size: 20, total_pages: 1, total_records: 3 }
      }
    })
    render(<MarketTransactionsPage />)

    const playerSelect = await screen.findByRole('combobox', { name: /buscar jugador por nombre/i })
    expect(within(playerSelect).getByRole('option', { name: 'Jugador sin nombre disponible' })).toHaveValue('33333333-3333-4333-8333-333333333333')
    const transactionCards = await screen.findAllByRole('article')
    expect(transactionCards.some((card) => within(card).queryByText('Jugador sin nombre disponible'))).toBe(true)
    expect(screen.getAllByText('Kenny Tete')).toHaveLength(2)
    expect(screen.queryByText(firstPlayerId)).not.toBeInTheDocument()
    expect(screen.queryByText(secondPlayerId)).not.toBeInTheDocument()

    expect(within(playerSelect).getByRole('option', { name: 'Kenny Tete (1)' })).toHaveValue(firstPlayerId)
    expect(within(playerSelect).getByRole('option', { name: 'Kenny Tete (2)' })).toHaveValue(secondPlayerId)
    await user.selectOptions(playerSelect, firstPlayerId)
    await user.selectOptions(screen.getByRole('combobox', { name: 'Tipo' }), 'SALE')
    fireEvent.change(screen.getByLabelText('Desde'), { target: { value: '2026-10-19T10:00' } })
    fireEvent.change(screen.getByLabelText('Hasta'), { target: { value: '2026-10-20T10:00' } })
    expect(playerSelect).toHaveValue(firstPlayerId)
    expect(screen.getByRole('combobox', { name: 'Tipo' })).toHaveValue('SALE')
    expect(screen.getByLabelText('Desde')).toHaveValue('2026-10-19T10:00')
    expect(screen.getByLabelText('Hasta')).toHaveValue('2026-10-20T10:00')
    marketApi.getMarketTransactions.mockResolvedValueOnce({ data: { transactions: [], pagination: { current_page: 1, page_size: 20, total_pages: 0, total_records: 0 } } })
    await user.click(screen.getByRole('button', { name: 'Aplicar filtros' }))

    await waitFor(() => expect(marketApi.getMarketTransactions).toHaveBeenCalledWith({
      jwt: 'jwt-test',
      filters: {
        playerId: firstPlayerId,
        type: 'SALE',
        from: new Date('2026-10-19T10:00').toISOString(),
        to: new Date('2026-10-20T10:00').toISOString(),
        page: 1,
        limit: 20
      }
    }))

    marketApi.getMarketTransactions.mockResolvedValueOnce({ data: { transactions: [], pagination: { current_page: 1, page_size: 20, total_pages: 0, total_records: 0 } } })
    await user.click(screen.getByRole('button', { name: 'Limpiar filtros' }))
    await waitFor(() => expect(marketApi.getMarketTransactions).toHaveBeenCalledWith(expect.objectContaining({
      filters: expect.objectContaining({ playerId: undefined, type: undefined, from: undefined, to: undefined, page: 1, limit: 20 })
    })))
  })

  it('acumula opciones al cambiar de página sin cargar el catálogo completo', async () => {
    const user = userEvent.setup()
    const firstPage = {
      data: {
        transactions: [{ transaction_id: 'tx-1', confirmed_at: '2026-10-19T12:00:00Z', player_name_snapshot: 'Jugador página 1', player_id: '11111111-1111-4111-8111-111111111111', operation_type: 'SALE', quantity: 1, unit_price: 1, total_amount: 1, quote_version: 1, realized_pnl: 0 }],
        pagination: { current_page: 1, page_size: 20, total_pages: 2, total_records: 21 }
      }
    }
    const secondPage = {
      data: {
        transactions: [{ transaction_id: 'tx-2', confirmed_at: '2026-10-18T12:00:00Z', player_name_snapshot: 'Jugador página 2', player_id: '22222222-2222-4222-8222-222222222222', operation_type: 'PURCHASE', quantity: 1, unit_price: 1, total_amount: 1, quote_version: 1, realized_pnl: null }],
        pagination: { current_page: 2, page_size: 20, total_pages: 2, total_records: 21 }
      }
    }
    marketApi.getMarketTransactions.mockImplementation(({ filters }) => {
      if (filters.limit === 100) return Promise.resolve({ ...firstPage, data: { ...firstPage.data, pagination: { ...firstPage.data.pagination, total_pages: 1 } } })
      return Promise.resolve(filters.page === 2 ? secondPage : firstPage)
    })
    render(<MarketTransactionsPage />)
    const firstPageCards = await screen.findAllByRole('article')
    expect(firstPageCards.some((card) => within(card).queryByText('Jugador página 1'))).toBe(true)
    await user.click(screen.getByRole('button', { name: 'Siguiente' }))

    await waitFor(() => {
      const secondPageCards = screen.getAllByRole('article')
      expect(secondPageCards.some((card) => within(card).queryByText('Jugador página 2'))).toBe(true)
    })
    const playerSelect = screen.getByRole('combobox', { name: /buscar jugador por nombre/i })
    expect(within(playerSelect).getByRole('option', { name: 'Jugador página 1' })).toHaveValue('11111111-1111-4111-8111-111111111111')
    expect(within(playerSelect).getByRole('option', { name: 'Jugador página 2' })).toHaveValue('22222222-2222-4222-8222-222222222222')
    expect(marketApi.getMarketTransactions).toHaveBeenCalledWith(expect.objectContaining({ filters: expect.objectContaining({ page: 2, limit: 20 }) }))
  })
})
