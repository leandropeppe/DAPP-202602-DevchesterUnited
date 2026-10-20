import { render, screen, within } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import MarketStateMessage from '../src/components/MarketStateMessage.jsx'
import OperationStatus from '../src/components/OperationStatus.jsx'
import QuoteStatus from '../src/components/QuoteStatus.jsx'
import MarketPlayerCard from '../src/components/MarketPlayerCard.jsx'
import MarketPage from '../src/views/MarketPage.jsx'
import { quotePresentationStatus } from '../src/market/marketState.js'

const marketApi = vi.hoisted(() => ({
  getMarketBalance: vi.fn(),
  getMarketPlayers: vi.fn()
}))
const marketSession = vi.hoisted(() => ({ jwt: 'jwt-market', isJwtExpired: false }))

vi.mock('../src/api/http.js', () => marketApi)
vi.mock('../src/session/SessionContext.jsx', () => ({
  useSession: () => marketSession
}))

const currentPlayer = {
  id: 'ply_11111111-1111-4111-8111-111111111111',
  name: 'Jugador vigente',
  league: 'Liga',
  team: 'Equipo',
  player_active: true,
  team_active: true,
  tokens_available: 25,
  operable: true,
  quote_status: 'VALID',
  quote_availability: 'CURRENT',
  quote_id: '22222222-2222-4222-8222-222222222222',
  quote_version: 7,
  price_per_token: 12.5
}

beforeEach(() => {
  vi.clearAllMocks()
  marketSession.jwt = 'jwt-market'
  marketSession.isJwtExpired = false
  marketApi.getMarketBalance.mockResolvedValue({ data: { balance: 1500 } })
  marketApi.getMarketPlayers.mockResolvedValue({
    data: {
      players: [currentPlayer, {
        ...currentPlayer,
        id: 'ply_33333333-3333-4333-8333-333333333333',
        name: 'Jugador histórico',
        quote_status: 'REPLACED',
        quote_availability: 'HISTORICAL',
        operable: false
      }],
      pagination: { current_page: 1, total_pages: 1, total_records: 2 }
    }
  })
})

describe('componentes de estado del mercado', () => {
  it('muestra el estado de carga como información accesible', () => {
    render(<MarketStateMessage status="loading" />)

    expect(screen.getByRole('status')).toHaveTextContent('Cargando información del mercado')
  })

  it('muestra errores y mercado no disponible como alertas', () => {
    const { rerender } = render(<MarketStateMessage status="error" />)

    expect(screen.getByRole('alert')).toHaveTextContent('No fue posible cargar')

    rerender(<MarketStateMessage status="unavailable" />)

    expect(screen.getByRole('alert')).toHaveTextContent('no está disponible temporalmente')
  })

  it('muestra una cotización disponible sin calcular el precio recibido', () => {
    render(<QuoteStatus status="available" price="1.00" />)

    expect(screen.getByRole('status')).toHaveTextContent('Cotización disponible')
    expect(screen.getByRole('status')).toHaveTextContent('Precio: 1.00')
  })

  it.each([
    ['unavailable', 'Cotización no disponible'],
    ['historical', 'Cotización histórica'],
    ['replaced', 'Cotización reemplazada'],
    ['invalidated', 'Cotización invalidada'],
    ['expired', 'Cotización expirada']
  ])('comunica que la cotización %s no es operable', (status, message) => {
    render(<QuoteStatus status={status} />)

    expect(screen.getByRole('status')).toHaveTextContent(message)
    expect(screen.getByRole('status')).toHaveTextContent('no está disponible para operar')
  })

  it.each([
    'unavailable',
    'historical',
    'replaced',
    'invalidated',
    'expired'
  ])('conserva la advertencia obligatoria con mensaje personalizado para %s', (status) => {
    render(<QuoteStatus status={status} message="Cotización del jugador" />)

    const statusMessage = screen.getByRole('status')
    expect(statusMessage).toHaveTextContent('Cotización del jugador')
    expect(statusMessage).toHaveTextContent('no está disponible para operar')
  })

  it('conserva los mensajes obligatorios para un estado desconocido personalizado', () => {
    render(<QuoteStatus status="future-status" message="Cotización del jugador" />)

    const statusMessage = screen.getByRole('status')
    expect(statusMessage).toHaveTextContent('Cotización del jugador')
    expect(statusMessage).toHaveTextContent('no se puede determinar')
    expect(statusMessage).toHaveTextContent('no está disponible para operar')
  })

  it('conserva el mensaje personalizado y el precio para una cotización disponible', () => {
    render(<QuoteStatus status="available" message="Cotización del jugador" price="1.00" />)

    const statusMessage = screen.getByRole('status')
    expect(statusMessage).toHaveTextContent('Cotización del jugador')
    expect(statusMessage).toHaveTextContent('Precio: 1.00')
    expect(statusMessage).not.toHaveTextContent('no está disponible para operar')
  })

  it('no muestra el precio cuando la cotización no es operable', () => {
    render(<QuoteStatus status="expired" price="999.99" />)

    expect(screen.getByRole('status')).not.toHaveTextContent('Precio: 999.99')
  })

  it.each([
    'unavailable',
    'historical',
    'replaced',
    'invalidated',
    'expired'
  ])('no duplica la advertencia predeterminada para %s', (status) => {
    render(<QuoteStatus status={status} />)

    const message = screen.getByRole('status').textContent
    expect(message.match(/no está disponible para operar/g)).toHaveLength(1)
  })

  it('trata un estado de cotización desconocido como no operable', () => {
    render(<QuoteStatus status="future-status" />)

    expect(screen.getByRole('status')).toHaveTextContent('no se puede determinar')
    expect(screen.getByRole('status')).not.toHaveClass('status-message--success')
  })

  it.each([
    ['pending', 'pendiente de confirmación', 'status'],
    ['confirmed', 'fue confirmada', 'status'],
    ['rejected', 'fue rechazada', 'alert'],
    ['uncertain', 'resultado de la operación es incierto', 'alert']
  ])('muestra la operación %s con una semántica clara', (status, message, role) => {
    render(<OperationStatus status={status} />)

    expect(screen.getByRole(role)).toHaveTextContent(message)
  })

  it('trata una operación desconocida como incierta y no confirmada', () => {
    render(<OperationStatus status="future-status" />)

    expect(screen.getByRole('alert')).toHaveTextContent('no se puede determinar')
    expect(screen.getByRole('alert')).not.toHaveTextContent('confirmada')
  })

  it.each([
    [{ quoteStatus: 'VALID', quoteAvailability: 'CURRENT', operable: true }, 'available'],
    [{ quoteStatus: 'VALID', quoteAvailability: 'UNKNOWN', operable: true }, 'unknown'],
    [{ quoteStatus: 'VALID', quoteAvailability: 'CURRENT', operable: false }, 'unknown'],
    [{ quoteStatus: 'UNKNOWN', quoteAvailability: 'CURRENT', operable: true }, 'unknown'],
    [{ quoteStatus: 'VALID', quoteAvailability: null, operable: true }, 'unknown'],
    [{ quoteStatus: 'VALID', quoteAvailability: 'ABSENT', operable: true }, 'unavailable'],
    [{ quoteStatus: 'VALID', quoteAvailability: 'HISTORICAL', operable: true }, 'historical'],
    [{ quoteStatus: 'REPLACED', quoteAvailability: 'CURRENT', operable: false }, 'replaced'],
    [{ quoteStatus: 'INVALIDATED', quoteAvailability: 'CURRENT', operable: false }, 'invalidated'],
    [{ quoteStatus: 'EXPIRED', quoteAvailability: 'CURRENT', operable: false }, 'expired']
  ])('presenta de forma conservadora la combinación de cotización %#', (quote, expected) => {
    expect(quotePresentationStatus(quote)).toBe(expected)
  })

  it('no presenta como operable una cotización desconocida', () => {
    render(
      <MarketPlayerCard
        player={{
          id: 'ply_11111111-1111-4111-8111-111111111111',
          name: 'Jugador de prueba',
          team: 'Equipo',
          league: 'Liga',
          player_active: true,
          team_active: true,
          tokens_available: 10,
          operable: true,
          quote_status: 'VALID',
          quote_availability: 'UNKNOWN',
          price_per_token: 1,
          quote_version: 1
        }}
      />
    )

    expect(screen.getByText('No operable')).toBeInTheDocument()
    expect(screen.getByRole('status')).not.toHaveTextContent('Cotización disponible')
  })

  it('renderiza saldo, cotización vigente y cotización histórica sin habilitar la histórica', async () => {
    render(<MarketPage onNavigate={vi.fn()} />)

    expect(await screen.findByRole('heading', { name: 'Mercado de jugadores' })).toBeInTheDocument()
    expect(screen.getByLabelText('Saldo disponible')).toHaveTextContent('1.500,00')
    expect(screen.getByRole('button', { name: 'Comprar' })).toBeInTheDocument()
    const historicalCard = screen.getByRole('heading', { name: 'Jugador histórico' }).closest('article')
    expect(historicalCard).not.toBeNull()
    expect(historicalCard.querySelector('button')).toBeNull()
    const historicalStatus = within(historicalCard).getByRole('status')
    expect(historicalStatus).toHaveTextContent('Cotización histórica.')
    expect(historicalStatus).toHaveTextContent('no está disponible para operar.')
    expect(marketApi.getMarketBalance).toHaveBeenCalledWith('jwt-market')
    expect(marketApi.getMarketPlayers).toHaveBeenCalledWith({ jwt: 'jwt-market', filters: { page: 1, limit: 20 } })
  })

  it('no consulta el mercado sin JWT', async () => {
    marketSession.jwt = null
    render(<MarketPage onNavigate={vi.fn()} />)

    expect(await screen.findByRole('alert')).toHaveTextContent(/iniciá sesión/i)
    expect(marketApi.getMarketBalance).not.toHaveBeenCalled()
    expect(marketApi.getMarketPlayers).not.toHaveBeenCalled()
  })
})
