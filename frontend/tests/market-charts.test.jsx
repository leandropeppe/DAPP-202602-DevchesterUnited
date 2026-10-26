import { render, screen, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import MarketPortfolioCharts, { buildPortfolioChartData } from '../src/components/MarketPortfolioCharts.jsx'
import MarketTransactionActivityChart, { aggregateTransactionActivity } from '../src/components/MarketTransactionActivityChart.jsx'

const marketApi = vi.hoisted(() => ({ getMarketTransactions: vi.fn() }))

vi.mock('../src/api/http.js', () => marketApi)
vi.mock('recharts', () => {
  const Container = ({ children }) => <div>{children}</div>
  const Leaf = () => null
  return {
    Bar: Leaf,
    BarChart: Container,
    CartesianGrid: Leaf,
    Cell: Leaf,
    Legend: Leaf,
    Line: Leaf,
    LineChart: Container,
    Pie: Container,
    PieChart: Container,
    ResponsiveContainer: Container,
    Tooltip: Leaf,
    XAxis: Leaf,
    YAxis: Leaf
  }
})

beforeEach(() => vi.clearAllMocks())

describe('adaptadores de gráficos del portfolio', () => {
  it('usa solo valores CURRENT válidos y conserva exclusiones explícitas', () => {
    const result = buildPortfolioChartData([
      { player_id: 'one', player_name: 'Jugador Uno', quote_availability: 'CURRENT', current_value: 60, pending_acquisition_cost: 50 },
      { player_id: 'two', player_name: 'Jugador Dos', quote_availability: 'HISTORICAL', current_value: 40, pending_acquisition_cost: 30 },
      { player_id: 'three', player_name: 'Jugador Tres', quote_availability: 'ABSENT', current_value: null, pending_acquisition_cost: 20 }
    ])

    expect(result.distributionData).toHaveLength(1)
    expect(result.distributionData[0]).toMatchObject({ playerId: 'one', value: 60, percentage: 100 })
    expect(result.comparisonData).toHaveLength(1)
    expect(result.excludedPositions.map((item) => item.quoteStatus)).toEqual(['Cotización histórica', 'Sin cotización'])
  })

  it('evita gráficos engañosos cuando el portfolio está vacío o vale cero', () => {
    expect(buildPortfolioChartData([]).distributionData).toEqual([])
    expect(buildPortfolioChartData([{ player_id: 'zero', quote_availability: 'CURRENT', current_value: 0, pending_acquisition_cost: 0 }]).distributionData).toEqual([])
    render(<MarketPortfolioCharts positions={[]} />)
    expect(screen.getByText(/no hay valores actuales suficientes/i)).toBeInTheDocument()
    expect(screen.getByText(/no hay posiciones con costo y valor actual comparables/i)).toBeInTheDocument()
  })

  it('expone títulos, gráficos y tablas equivalentes con nombres y valores', () => {
    render(<MarketPortfolioCharts positions={[{ player_id: 'one', player_name: 'Jugador Uno', quote_availability: 'CURRENT', current_value: 60, pending_acquisition_cost: 50 }]} />)

    expect(screen.getByRole('heading', { name: 'Visualizaciones del portfolio' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Distribución del portfolio' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Costo pendiente vs. valor actual' })).toBeInTheDocument()
    expect(screen.getByRole('img', { name: /distribución del valor actual/i })).toBeInTheDocument()
    expect(screen.getByRole('img', { name: /comparación de costo pendiente/i })).toBeInTheDocument()
    expect(screen.getAllByRole('table')).toHaveLength(2)
    expect(screen.getAllByText('Jugador Uno')).toHaveLength(2)
  })
})

describe('adaptador de actividad de transacciones', () => {
  it('agrupa compras y ventas por día en una zona horaria determinista', () => {
    const originalDateTimeFormat = Intl.DateTimeFormat
    const dateTimeFormat = vi.spyOn(Intl, 'DateTimeFormat').mockImplementation(function (locale, options) {
      return new originalDateTimeFormat(locale, { ...options, timeZone: 'America/Argentina/Buenos_Aires' })
    })
    try {
      expect(aggregateTransactionActivity([
        { confirmed_at: '2026-10-23T01:50:54Z', operation_type: 'SALE', quantity: 1 },
        { confirmed_at: '2026-10-23T02:10:00Z', operation_type: 'PURCHASE', quantity: 3 },
        { confirmed_at: '2026-10-24T03:00:00Z', operation_type: 'PURCHASE', quantity: 2 }
      ])).toEqual([
        { day: '2026-10-22', label: '22/10/2026', purchases: 3, sales: 1 },
        { day: '2026-10-24', label: '24/10/2026', purchases: 2, sales: 0 }
      ])
    } finally {
      dateTimeFormat.mockRestore()
    }
  })

  it('carga todas las páginas acotadas y diferencia compras de ventas', async () => {
    marketApi.getMarketTransactions
      .mockResolvedValueOnce({ data: { transactions: [{ confirmed_at: '2026-10-22T12:00:00Z', operation_type: 'PURCHASE', quantity: 3 }], pagination: { current_page: 1, page_size: 100, total_pages: 2, total_records: 2 } } })
      .mockResolvedValueOnce({ data: { transactions: [{ confirmed_at: '2026-10-23T12:00:00Z', operation_type: 'SALE', quantity: 1 }], pagination: { current_page: 2, page_size: 100, total_pages: 2, total_records: 2 } } })

    render(<MarketTransactionActivityChart jwt="jwt-test" filters={{ playerId: '', type: '', from: '', to: '' }} />)

    expect(await screen.findByText('22/10/2026')).toBeInTheDocument()
    expect(screen.getByText('23/10/2026')).toBeInTheDocument()
    expect(screen.getByText(/historial completo cargado/i)).toBeInTheDocument()
    await waitFor(() => expect(marketApi.getMarketTransactions).toHaveBeenCalledTimes(2))
    expect(marketApi.getMarketTransactions.mock.calls[0][0].filters).toMatchObject({ page: 1, limit: 100 })
    expect(marketApi.getMarketTransactions.mock.calls[1][0].filters).toMatchObject({ page: 2, limit: 100 })
  })

  it('informa cuando el historial supera el límite seguro de páginas', async () => {
    marketApi.getMarketTransactions.mockResolvedValue({
      data: {
        transactions: [{ confirmed_at: '2026-10-22T12:00:00Z', operation_type: 'PURCHASE', quantity: 1 }],
        pagination: { current_page: 1, page_size: 100, total_pages: 21, total_records: 2100 }
      }
    })

    render(<MarketTransactionActivityChart jwt="jwt-test" filters={{ playerId: '', type: '', from: '', to: '' }} />)

    expect(await screen.findByText(/primeras 20 de 21 páginas/i)).toBeInTheDocument()
    await waitFor(() => expect(marketApi.getMarketTransactions).toHaveBeenCalledTimes(20))
  })

  it('muestra estados vacío y error', async () => {
    marketApi.getMarketTransactions.mockResolvedValueOnce({ data: { transactions: [], pagination: { current_page: 1, page_size: 100, total_pages: 0, total_records: 0 } } })
    const { unmount } = render(<MarketTransactionActivityChart jwt="jwt-test" filters={{ playerId: '', type: '', from: '', to: '' }} />)
    expect(await screen.findByText(/no hay operaciones confirmadas/i)).toBeInTheDocument()
    unmount()

    marketApi.getMarketTransactions.mockRejectedValueOnce(new Error('fallo de red'))
    render(<MarketTransactionActivityChart jwt="jwt-test" filters={{ playerId: '', type: '', from: '', to: '' }} />)
    expect(await screen.findByRole('alert')).toHaveTextContent('fallo de red')
  })

  it('expone una tabla equivalente para la actividad temporal', async () => {
    marketApi.getMarketTransactions.mockResolvedValueOnce({
      data: {
        transactions: [{ confirmed_at: '2026-10-22T12:00:00Z', operation_type: 'PURCHASE', quantity: 3 }],
        pagination: { current_page: 1, page_size: 100, total_pages: 1, total_records: 1 }
      }
    })

    render(<MarketTransactionActivityChart jwt="jwt-test" filters={{ playerId: '', type: '', from: '', to: '' }} />)

    expect(await screen.findByRole('img', { name: /evolución diaria/i })).toBeInTheDocument()
    expect(screen.getByRole('table', { name: /actividad diaria/i })).toBeInTheDocument()
    expect(screen.getByText('Compras')).toBeInTheDocument()
    expect(screen.getByText('Ventas')).toBeInTheDocument()
  })
})
