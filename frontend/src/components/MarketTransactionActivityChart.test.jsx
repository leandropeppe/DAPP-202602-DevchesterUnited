import { render, screen, waitFor } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import MarketTransactionActivityChart, { aggregateTransactionActivity } from './MarketTransactionActivityChart.jsx'

const api = vi.hoisted(() => ({ getMarketTransactions: vi.fn() }))
vi.mock('../api/http.js', () => api)
vi.mock('recharts', () => ({
  CartesianGrid: () => null,
  Legend: () => null,
  Line: () => null,
  LineChart: ({ children }) => <div>{children}</div>,
  ResponsiveContainer: ({ children }) => <div>{children}</div>,
  Tooltip: () => null,
  XAxis: () => null,
  YAxis: () => null
}))

function result (totalPages = 1) {
  return {
    data: {
      transactions: [{ operation_type: 'PURCHASE', quantity: 3, confirmed_at: '2026-10-29T12:00:00Z' }],
      pagination: { current_page: 1, page_size: 100, total_pages: totalPages, total_records: totalPages }
    }
  }
}

describe('MarketTransactionActivityChart', () => {
  it('consulta una base estable de 100 registros y no depende de la página visible', async () => {
    api.getMarketTransactions.mockResolvedValue(result())
    const filters = { playerId: '', type: '', from: '', to: '' }
    const { rerender } = render(<MarketTransactionActivityChart jwt="jwt" filters={filters} />)

    await waitFor(() => expect(api.getMarketTransactions).toHaveBeenCalledTimes(1))
    expect(api.getMarketTransactions).toHaveBeenCalledWith({
      jwt: 'jwt',
      filters: expect.objectContaining({ page: 1, limit: 100 })
    })
    rerender(<MarketTransactionActivityChart jwt="jwt" filters={filters} />)
    expect(api.getMarketTransactions).toHaveBeenCalledTimes(1)
  })

  it('conserva el indicador de historial parcial cuando supera el límite consultable', async () => {
    api.getMarketTransactions.mockImplementation(({ filters }) => Promise.resolve(result(filters.page === 1 ? 25 : 25)))
    render(<MarketTransactionActivityChart jwt="jwt" filters={{ playerId: '', type: '', from: '', to: '' }} />)

    expect(await screen.findByText(/primeras 20 de 25 páginas/)).toBeInTheDocument()
  })

  it('agrega compras y ventas por día', () => {
    expect(aggregateTransactionActivity([
      { operation_type: 'PURCHASE', quantity: 2, confirmed_at: '2026-10-29T12:00:00Z' },
      { operation_type: 'SALE', quantity: 1, confirmed_at: '2026-10-29T14:00:00Z' }
    ])).toEqual([{ day: expect.any(String), label: expect.any(String), purchases: 2, sales: 1 }])
  })
})
